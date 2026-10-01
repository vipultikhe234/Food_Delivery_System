#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import { validateAll } from "./checks.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");

const { values } = parseArgs({
  options: {
    file: { type: "string", default: resolve(repoRoot, "docs/requirements/requirements.json") },
    schema: { type: "string", default: resolve(repoRoot, "docs/requirements/requirements.schema.json") },
    base: { type: "string" },
  },
});

const readJson = (path) => JSON.parse(readFileSync(path, "utf8").replace(/^\uFEFF/, ""));

let doc;
let schema;
let base;
try {
  doc = readJson(values.file);
  schema = readJson(values.schema);
  base = values.base ? readJson(values.base) : undefined;
} catch (error) {
  console.error(`requirements-validator: cannot read input: ${error.message}`);
  process.exit(2);
}

const errors = validateAll(doc, schema, base);
if (errors.length > 0) {
  console.error(`requirements-validator: ${errors.length} problem(s) found`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}

const counts = doc.requirements.reduce((acc, r) => ({ ...acc, [r.status]: (acc[r.status] ?? 0) + 1 }), {});
console.log(
  `requirements-validator: OK — ${doc.requirements.length} requirements, ` +
    `${doc.nonFunctionalRequirements.length} NFRs, ${doc.openQuestions.length} open questions; ` +
    `status ${JSON.stringify(counts)}${base ? "; history checked against base" : ""}`,
);
