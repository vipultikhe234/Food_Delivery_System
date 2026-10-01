import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { describe, it } from "node:test";
import { fileURLToPath } from "node:url";
import {
  checkAgainstBase,
  checkReferences,
  checkStatusHistory,
  checkUniqueIds,
  validateAll,
} from "../src/checks.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
const readJson = (p) => JSON.parse(readFileSync(resolve(repoRoot, p), "utf8"));
const schema = readJson("docs/requirements/requirements.schema.json");

const statusModel = {
  allowedStatuses: ["DRAFT", "ANALYSIS", "APPROVED", "DESIGN", "DEVELOPMENT", "CODE_REVIEW", "TESTING", "BUG_FOUND", "REWORK", "UAT", "READY_FOR_RELEASE", "RELEASED", "COMPLETED", "BLOCKED", "CANCELLED"],
  transitions: {
    DRAFT: ["ANALYSIS", "CANCELLED"],
    ANALYSIS: ["APPROVED", "DRAFT", "BLOCKED", "CANCELLED"],
    APPROVED: ["DESIGN", "BLOCKED", "CANCELLED"],
    DESIGN: ["DEVELOPMENT", "BLOCKED", "CANCELLED"],
    DEVELOPMENT: ["CODE_REVIEW", "BLOCKED", "CANCELLED"],
    CODE_REVIEW: ["TESTING", "REWORK"],
    TESTING: ["UAT", "BUG_FOUND"],
    BUG_FOUND: ["REWORK"],
    REWORK: ["CODE_REVIEW"],
    UAT: ["READY_FOR_RELEASE", "BUG_FOUND"],
    READY_FOR_RELEASE: ["RELEASED", "BLOCKED"],
    RELEASED: ["COMPLETED", "BUG_FOUND"],
    BLOCKED: ["<status held before BLOCKED>", "CANCELLED"],
    COMPLETED: [],
    CANCELLED: [],
  },
  evidenceGates: {},
  versioningRules: [],
};

const approvedHistory = () => [
  { version: 1, from: "DRAFT", to: "ANALYSIS", date: "2026-10-01", by: "RequirementAgent", reason: "analysis" },
  { version: 1, from: "ANALYSIS", to: "APPROVED", date: "2026-10-01", by: "Project owner", reason: "approved" },
];

function requirement(id, overrides = {}) {
  return {
    id,
    title: `Requirement ${id}`,
    module: id.split("-")[1],
    version: 1,
    status: "APPROVED",
    priority: "P0",
    phase: 4,
    targetRelease: "v0.1.0",
    source: ["MP §1"],
    businessRequirement: ["BR-01"],
    description: "Description",
    technicalRequirement: "Technical",
    dependencies: [],
    acceptanceCriteria: ["AC1: Something observable happens."],
    apiRequirements: [],
    databaseRequirements: [],
    uiRequirements: [],
    mobileRequirements: [],
    testingRequirements: [],
    securityRequirements: [],
    performanceRequirements: [],
    assignedAgent: "BackendAgent",
    assignedDeveloper: null,
    createdBy: "RequirementAgent",
    createdDate: "2026-10-01",
    updatedDate: "2026-10-01",
    changeHistory: [{ version: 1, date: "2026-10-01", by: "RequirementAgent", summary: "Initial" }],
    statusHistory: approvedHistory(),
    ...overrides,
  };
}

function documentWith(requirements) {
  return {
    schemaVersion: "1.0",
    document: { project: "FDP", projectCode: "FDP", documentVersion: "1.0.0", stage: "test", status: "APPROVED", createdDate: "2026-10-01", updatedDate: "2026-10-01", createdBy: "test" },
    priorityLegend: { P0: "must", P1: "should", P2: "could" },
    statusModel,
    businessRequirements: [{ id: "BR-01", title: "Order food", source: ["MP §1"] }],
    actors: [],
    modules: [{ code: "PLAT", name: "Platform", owner: "backend" }, { code: "ORDER", name: "Order", owner: "order-service" }],
    requirements,
    nonFunctionalRequirements: [
      { id: "NFR-PERF-001", category: "Performance", requirement: "Fast", target: "p95 < 300 ms", measurement: "k6", priority: "P0", status: "APPROVED", verified: false, source: ["MP §47"], version: 1 },
    ],
    phaseOrderNotes: [],
    roadmap: [],
    openQuestions: [{ id: "OQ-01", question: "?", recommendation: "r", decision: null }],
  };
}

const clone = (v) => structuredClone(v);

describe("real requirements file", () => {
  it("passes all checks", () => {
    const doc = readJson("docs/requirements/requirements.json");
    assert.deepEqual(validateAll(doc, schema), []);
  });
});

describe("fixture baseline", () => {
  it("is valid", () => {
    const doc = documentWith([requirement("REQ-PLAT-001"), requirement("REQ-ORDER-001", { dependencies: ["REQ-PLAT-001"] })]);
    assert.deepEqual(validateAll(doc, schema), []);
  });
});

describe("schema", () => {
  it("rejects a requirement missing mandatory fields", () => {
    const req = requirement("REQ-PLAT-001");
    delete req.acceptanceCriteria;
    const errors = validateAll(documentWith([req]), schema);
    assert.ok(errors.some((e) => e.includes("acceptanceCriteria")), errors.join("\n"));
  });

  it("rejects an unknown status", () => {
    const errors = validateAll(documentWith([requirement("REQ-PLAT-001", { status: "DONE" })]), schema);
    assert.ok(errors.some((e) => e.startsWith("schema:")), errors.join("\n"));
  });
});

describe("ids and references", () => {
  it("detects duplicate ids", () => {
    const errors = checkUniqueIds(documentWith([requirement("REQ-PLAT-001"), requirement("REQ-PLAT-001")]));
    assert.deepEqual(errors, ["requirements: duplicate id REQ-PLAT-001"]);
  });

  it("detects unknown dependencies and modules", () => {
    const errors = checkReferences(documentWith([requirement("REQ-PLAT-001", { dependencies: ["REQ-PLAT-999"] }), requirement("REQ-CART-001")]));
    assert.ok(errors.includes("REQ-PLAT-001: unknown dependency REQ-PLAT-999"));
    assert.ok(errors.includes("REQ-CART-001: unknown module CART"));
  });

  it("detects dependency cycles", () => {
    const errors = checkReferences(
      documentWith([requirement("REQ-PLAT-001", { dependencies: ["REQ-ORDER-001"] }), requirement("REQ-ORDER-001", { dependencies: ["REQ-PLAT-001"] })]),
    );
    assert.ok(errors.some((e) => e.startsWith("dependency cycle:")), errors.join("\n"));
  });

  it("checks every id in a combined phase-order note", () => {
    const doc = documentWith([requirement("REQ-PLAT-001"), requirement("REQ-ORDER-001")]);
    doc.phaseOrderNotes = [{ requirement: "REQ-PLAT-001 / REQ-ORDER-001", dependsOn: "REQ-ORDER-002", resolution: "stub" }];
    assert.deepEqual(validateAll(doc, schema), ["phaseOrderNotes: unknown requirement REQ-ORDER-002"]);
  });

  it("detects duplicate acceptance criterion numbers", () => {
    const errors = checkReferences(documentWith([requirement("REQ-PLAT-001", { acceptanceCriteria: ["AC1: a", "AC1: b"] })]));
    assert.ok(errors.includes("REQ-PLAT-001: duplicate acceptance criterion AC1"));
  });
});

describe("status history", () => {
  it("rejects a transition the status model does not allow", () => {
    const history = [...approvedHistory(), { version: 1, from: "APPROVED", to: "TESTING", date: "2026-10-02", by: "Dev", reason: "skip" }];
    const errors = checkStatusHistory(documentWith([requirement("REQ-PLAT-001", { status: "TESTING", statusHistory: history })]));
    assert.ok(errors.some((e) => e.includes("APPROVED->TESTING: transition not allowed")), errors.join("\n"));
  });

  it("rejects a status that does not match the last history entry", () => {
    const errors = checkStatusHistory(documentWith([requirement("REQ-PLAT-001", { status: "DESIGN" })]));
    assert.ok(errors.some((e) => e.includes("does not match last statusHistory entry")), errors.join("\n"));
  });

  it("rejects approval recorded by an agent", () => {
    const history = approvedHistory();
    history[1].by = "RequirementAgent";
    const errors = checkStatusHistory(documentWith([requirement("REQ-PLAT-001", { statusHistory: history })]));
    assert.ok(errors.some((e) => e.includes("named human approver")), errors.join("\n"));
  });

  it("requires test evidence with zero failures for TESTING->UAT", () => {
    const toTesting = [
      ...approvedHistory(),
      { version: 1, from: "APPROVED", to: "DESIGN", date: "2026-10-02", by: "a", reason: "r" },
      { version: 1, from: "DESIGN", to: "DEVELOPMENT", date: "2026-10-02", by: "a", reason: "r" },
      { version: 1, from: "DEVELOPMENT", to: "CODE_REVIEW", date: "2026-10-02", by: "a", reason: "r", evidence: { runId: "gh/1", commit: "abcdef1", pullRequest: "#1" } },
      { version: 1, from: "CODE_REVIEW", to: "TESTING", date: "2026-10-02", by: "a", reason: "r", evidence: { runId: "gh/2", commit: "abcdef2" } },
    ];
    const noEvidence = [...toTesting, { version: 1, from: "TESTING", to: "UAT", date: "2026-10-03", by: "a", reason: "r" }];
    const failing = [...toTesting, { version: 1, from: "TESTING", to: "UAT", date: "2026-10-03", by: "a", reason: "r", evidence: { runId: "gh/3", commit: "abcdef3", executed: 10, passed: 9, failed: 1 } }];
    const passing = [...toTesting, { version: 1, from: "TESTING", to: "UAT", date: "2026-10-03", by: "a", reason: "r", evidence: { runId: "gh/3", commit: "abcdef3", executed: 10, passed: 10, failed: 0 } }];

    const run = (h) => checkStatusHistory(documentWith([requirement("REQ-PLAT-001", { status: "UAT", statusHistory: h })]));
    assert.ok(run(noEvidence).some((e) => e.includes("evidence with runId and commit is required")));
    assert.ok(run(failing).some((e) => e.includes("zero failed tests")));
    assert.deepEqual(run(passing), []);
  });

  it("allows returning from BLOCKED only to the status held before", () => {
    const blocked = [...approvedHistory(), { version: 1, from: "APPROVED", to: "BLOCKED", date: "2026-10-02", by: "a", reason: "r" }];
    const back = [...blocked, { version: 1, from: "BLOCKED", to: "APPROVED", date: "2026-10-03", by: "a", reason: "r" }];
    const wrong = [...blocked, { version: 1, from: "BLOCKED", to: "DESIGN", date: "2026-10-03", by: "a", reason: "r" }];
    assert.deepEqual(checkStatusHistory(documentWith([requirement("REQ-PLAT-001", { status: "APPROVED", statusHistory: back })])), []);
    assert.ok(checkStatusHistory(documentWith([requirement("REQ-PLAT-001", { status: "DESIGN", statusHistory: wrong })])).length > 0);
  });
});

describe("history against base", () => {
  const base = documentWith([requirement("REQ-PLAT-001")]);

  it("rejects deleting a requirement", () => {
    const errors = checkAgainstBase(base, documentWith([]));
    assert.ok(errors.some((e) => e.includes("REQ-PLAT-001: removed")));
  });

  it("rejects rewriting status history", () => {
    const current = clone(base);
    current.requirements[0].statusHistory[1].reason = "rewritten";
    assert.ok(checkAgainstBase(base, current).some((e) => e.includes("statusHistory[1] was rewritten")));
  });

  it("rejects content changes without a new version", () => {
    const current = clone(base);
    current.requirements[0].acceptanceCriteria = ["AC1: Changed silently."];
    assert.ok(checkAgainstBase(base, current).some((e) => e.includes("without creating a new version")));
  });

  it("accepts a new version with impact report that re-enters ANALYSIS", () => {
    const current = clone(base);
    const req = current.requirements[0];
    req.version = 2;
    req.status = "ANALYSIS";
    req.acceptanceCriteria = ["AC1: Changed with a new version."];
    req.changeHistory.push({ version: 2, date: "2026-10-02", by: "ArchitectAgent", summary: "Change", impactReport: "docs/requirements/impact/IMPACT-0002.md", approvedBy: null });
    req.statusHistory.push({ version: 2, from: null, to: "ANALYSIS", date: "2026-10-02", by: "ArchitectAgent", reason: "new version" });
    assert.deepEqual(checkAgainstBase(base, current), []);
    assert.deepEqual(validateAll(current, schema, base), []);
  });

  it("allows recording the approver on a pending change entry", () => {
    const pending = clone(base);
    pending.requirements[0].changeHistory[0].approvedBy = null;
    const approved = clone(pending);
    approved.requirements[0].changeHistory[0].approvedBy = "Project owner";
    assert.deepEqual(checkAgainstBase(pending, approved), []);
  });

  it("rejects a new version without an impact report", () => {
    const current = clone(base);
    const req = current.requirements[0];
    req.version = 2;
    req.status = "ANALYSIS";
    req.changeHistory.push({ version: 2, date: "2026-10-02", by: "a", summary: "Change" });
    req.statusHistory.push({ version: 2, from: null, to: "ANALYSIS", date: "2026-10-02", by: "a", reason: "new version" });
    assert.ok(checkAgainstBase(base, current).some((e) => e.includes("has no impactReport")));
  });
});
