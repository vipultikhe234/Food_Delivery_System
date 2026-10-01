import Ajv2020 from "ajv/dist/2020.js";

const BLOCKED_RETURN_TOKEN = "<status held before BLOCKED>";

const CONTENT_FIELDS = [
  "title",
  "module",
  "priority",
  "source",
  "businessRequirement",
  "description",
  "technicalRequirement",
  "dependencies",
  "acceptanceCriteria",
  "apiRequirements",
  "databaseRequirements",
  "uiRequirements",
  "mobileRequirements",
  "testingRequirements",
  "securityRequirements",
  "performanceRequirements",
];

const NFR_CONTENT_FIELDS = ["category", "requirement", "target", "measurement", "priority"];

const EVIDENCE_RUN_GATES = new Set([
  "CODE_REVIEW->TESTING",
  "TESTING->UAT",
  "UAT->READY_FOR_RELEASE",
  "READY_FOR_RELEASE->RELEASED",
  "RELEASED->COMPLETED",
]);

export function checkSchema(doc, schema) {
  const ajv = new Ajv2020({ allErrors: true, strict: false });
  const validate = ajv.compile(schema);
  if (validate(doc)) return [];
  return validate.errors.map((e) => `schema: ${e.instancePath || "/"} ${e.message}`);
}

export function checkUniqueIds(doc) {
  const errors = [];
  const groups = {
    requirements: doc.requirements ?? [],
    nonFunctionalRequirements: doc.nonFunctionalRequirements ?? [],
    openQuestions: doc.openQuestions ?? [],
    businessRequirements: doc.businessRequirements ?? [],
  };
  for (const [name, items] of Object.entries(groups)) {
    const seen = new Set();
    for (const item of items) {
      if (seen.has(item.id)) errors.push(`${name}: duplicate id ${item.id}`);
      seen.add(item.id);
    }
  }
  return errors;
}

export function checkReferences(doc) {
  const errors = [];
  const reqIds = new Set(doc.requirements.map((r) => r.id));
  const moduleCodes = new Set(doc.modules.map((m) => m.code));
  const brIds = new Set(doc.businessRequirements.map((b) => b.id));

  for (const req of doc.requirements) {
    const idModule = req.id.split("-")[1];
    if (idModule !== req.module) errors.push(`${req.id}: id module ${idModule} differs from module ${req.module}`);
    if (!moduleCodes.has(req.module)) errors.push(`${req.id}: unknown module ${req.module}`);
    for (const dep of req.dependencies) {
      if (dep === req.id) errors.push(`${req.id}: depends on itself`);
      else if (!reqIds.has(dep)) errors.push(`${req.id}: unknown dependency ${dep}`);
    }
    for (const br of req.businessRequirement) {
      if (!brIds.has(br)) errors.push(`${req.id}: unknown business requirement ${br}`);
    }
    const acNumbers = req.acceptanceCriteria.map((ac) => ac.match(/^AC(\d+)/)?.[1]);
    const dupes = acNumbers.filter((n, i) => acNumbers.indexOf(n) !== i);
    for (const n of new Set(dupes)) errors.push(`${req.id}: duplicate acceptance criterion AC${n}`);
  }
  for (const note of doc.phaseOrderNotes) {
    for (const id of [...note.requirement.split(" / "), ...note.dependsOn.split(" / ")]) {
      if (!reqIds.has(id)) errors.push(`phaseOrderNotes: unknown requirement ${id}`);
    }
  }
  return errors.concat(findDependencyCycles(doc.requirements));
}

export function findDependencyCycles(requirements) {
  const graph = new Map(requirements.map((r) => [r.id, r.dependencies]));
  const state = new Map();
  const errors = [];
  const visit = (id, path) => {
    state.set(id, "visiting");
    for (const dep of graph.get(id) ?? []) {
      if (!graph.has(dep)) continue;
      if (state.get(dep) === "visiting") {
        errors.push(`dependency cycle: ${[...path.slice(path.indexOf(dep)), dep].join(" -> ")}`);
      } else if (!state.has(dep)) {
        visit(dep, [...path, dep]);
      }
    }
    state.set(id, "done");
  };
  for (const id of graph.keys()) if (!state.has(id)) visit(id, [id]);
  return errors;
}

export function checkStatusHistory(doc) {
  const { allowedStatuses, transitions } = doc.statusModel;
  const allowed = new Set(allowedStatuses);
  const errors = [];

  for (const req of doc.requirements) {
    const changeVersions = req.changeHistory.map((c) => c.version);
    changeVersions.forEach((v, i) => {
      if (v !== i + 1) errors.push(`${req.id}: changeHistory versions must be 1..N in order (found ${changeVersions.join(",")})`);
    });
    if (changeVersions.at(-1) !== req.version) {
      errors.push(`${req.id}: version ${req.version} does not match latest changeHistory version ${changeVersions.at(-1)}`);
    }

    let current = null;
    let heldBeforeBlocked = null;
    let lastVersion = 0;
    req.statusHistory.forEach((entry, index) => {
      const where = `${req.id}: statusHistory[${index}] ${entry.from ?? "null"}->${entry.to}`;
      if (entry.version < lastVersion) errors.push(`${where}: version goes backwards`);
      if (entry.version > req.version) errors.push(`${where}: version ${entry.version} is newer than requirement version`);
      if (!allowed.has(entry.to)) errors.push(`${where}: unknown status`);

      if (entry.from === null) {
        if (index > 0 && entry.version <= lastVersion) errors.push(`${where}: a null 'from' is only allowed when a new version starts`);
        if (!["DRAFT", "ANALYSIS"].includes(entry.to)) errors.push(`${where}: a new version must start in DRAFT or ANALYSIS`);
      } else {
        if (index > 0 && entry.version === lastVersion && entry.from !== current) {
          errors.push(`${where}: 'from' does not match previous status ${current}`);
        }
        if (entry.version > lastVersion && index > 0) {
          errors.push(`${where}: a new version must start with from=null`);
        }
        const targets = transitions[entry.from] ?? [];
        const isBlockedReturn = entry.from === "BLOCKED" && targets.includes(BLOCKED_RETURN_TOKEN) && entry.to === heldBeforeBlocked;
        if (!targets.includes(entry.to) && !isBlockedReturn) errors.push(`${where}: transition not allowed by statusModel`);
        errors.push(...checkEvidenceGate(req.id, index, entry));
      }

      if (entry.to === "BLOCKED") heldBeforeBlocked = entry.from;
      current = entry.to;
      lastVersion = entry.version;
    });

    if (current !== req.status) errors.push(`${req.id}: status ${req.status} does not match last statusHistory entry ${current}`);
    if (lastVersion !== req.version) errors.push(`${req.id}: last statusHistory entry is for version ${lastVersion}, requirement is version ${req.version}`);
  }

  for (const nfr of doc.nonFunctionalRequirements) {
    if (!allowed.has(nfr.status)) errors.push(`${nfr.id}: unknown status ${nfr.status}`);
  }
  return errors;
}

function checkEvidenceGate(reqId, index, entry) {
  const gate = `${entry.from}->${entry.to}`;
  const where = `${reqId}: statusHistory[${index}] ${gate}`;
  const errors = [];
  if (gate === "ANALYSIS->APPROVED" && /agent$/i.test(entry.by.trim())) {
    errors.push(`${where}: approval must be recorded by a named human approver, not an agent`);
  }
  if (gate === "DEVELOPMENT->CODE_REVIEW" && !entry.evidence?.pullRequest) {
    errors.push(`${where}: evidence.pullRequest is required`);
  }
  if (EVIDENCE_RUN_GATES.has(gate)) {
    if (!entry.evidence?.runId || !entry.evidence?.commit) {
      errors.push(`${where}: evidence with runId and commit is required`);
    } else if (gate === "TESTING->UAT") {
      if (!(entry.evidence.executed > 0)) errors.push(`${where}: evidence must report executed tests`);
      if (entry.evidence.failed !== 0) errors.push(`${where}: evidence must report zero failed tests`);
    }
  }
  return errors;
}

export function checkAgainstBase(base, current) {
  const errors = [];
  const currentReqs = new Map(current.requirements.map((r) => [r.id, r]));

  for (const before of base.requirements) {
    const after = currentReqs.get(before.id);
    if (!after) {
      errors.push(`${before.id}: removed; requirements are never deleted (use status CANCELLED)`);
      continue;
    }
    if (after.version < before.version) errors.push(`${before.id}: version decreased from ${before.version} to ${after.version}`);
    errors.push(...checkPrefix(before.id, "changeHistory", before.changeHistory, after.changeHistory, allowApprovalFill));
    errors.push(...checkPrefix(before.id, "statusHistory", before.statusHistory, after.statusHistory));

    const changed = CONTENT_FIELDS.filter((f) => canonical(before[f]) !== canonical(after[f]));
    if (changed.length > 0 && after.version === before.version) {
      errors.push(`${before.id}: content changed (${changed.join(", ")}) without creating a new version`);
    }
    if (after.version > before.version) {
      for (const change of after.changeHistory.filter((c) => c.version > before.version)) {
        if (!change.impactReport) errors.push(`${before.id}: version ${change.version} has no impactReport`);
      }
      const firstOfNewVersion = after.statusHistory.find((s) => s.version === before.version + 1);
      if (!firstOfNewVersion || firstOfNewVersion.to !== "ANALYSIS") {
        errors.push(`${before.id}: version ${before.version + 1} must re-enter ANALYSIS`);
      }
    }
  }

  const currentNfrs = new Map(current.nonFunctionalRequirements.map((n) => [n.id, n]));
  for (const before of base.nonFunctionalRequirements) {
    const after = currentNfrs.get(before.id);
    if (!after) {
      errors.push(`${before.id}: removed; requirements are never deleted (use status CANCELLED)`);
      continue;
    }
    const changed = NFR_CONTENT_FIELDS.filter((f) => canonical(before[f]) !== canonical(after[f]));
    if (changed.length > 0 && after.version <= before.version) {
      errors.push(`${before.id}: content changed (${changed.join(", ")}) without creating a new version`);
    }
  }

  const currentOqs = new Set(current.openQuestions.map((q) => q.id));
  for (const q of base.openQuestions) {
    if (!currentOqs.has(q.id)) errors.push(`${q.id}: open question removed; ids are never deleted`);
  }
  return errors;
}

function allowApprovalFill(before, after) {
  if (before.approvedBy === null && typeof after.approvedBy === "string") {
    return canonical({ ...before, approvedBy: after.approvedBy }) === canonical(after);
  }
  return false;
}

function checkPrefix(id, field, before, after, tolerated = () => false) {
  const errors = [];
  if (after.length < before.length) {
    errors.push(`${id}: ${field} shortened from ${before.length} to ${after.length} entries (history is append-only)`);
    return errors;
  }
  before.forEach((entry, i) => {
    if (canonical(entry) !== canonical(after[i]) && !tolerated(entry, after[i])) {
      errors.push(`${id}: ${field}[${i}] was rewritten (history is append-only)`);
    }
  });
  return errors;
}

export function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${canonical(value[k])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value ?? null);
}

export function validateAll(doc, schema, base) {
  const schemaErrors = checkSchema(doc, schema);
  if (schemaErrors.length > 0) return schemaErrors;
  return [
    ...checkUniqueIds(doc),
    ...checkReferences(doc),
    ...checkStatusHistory(doc),
    ...(base ? checkAgainstBase(base, doc) : []),
  ];
}
