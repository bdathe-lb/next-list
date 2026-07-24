import {readFileSync} from "node:fs";
import {resolve} from "node:path";
import {
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";

const projectId = "demo-nextlist";

function readRuleFile(fileName: string): string {
  return readFileSync(
    resolve(process.cwd(), "..", "firebase", fileName),
    "utf8",
  );
}

export function createRulesTestEnvironment(): Promise<RulesTestEnvironment> {
  return initializeTestEnvironment({
    projectId,
    firestore: {
      rules: readRuleFile("firestore.rules"),
    },
    storage: {
      rules: readRuleFile("storage.rules"),
    },
  });
}
