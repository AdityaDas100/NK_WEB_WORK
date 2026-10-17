import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { FlatCompat } from "@eslint/eslintrc";

const compat = new FlatCompat({
  baseDirectory: dirname(fileURLToPath(import.meta.url))
});

const eslintConfig = [
  {
    ignores: [".next/**", "node_modules/**", "next-env.d.ts"]
  },
  ...compat.extends("next/core-web-vitals", "next/typescript"),
  {
    rules: {
      // The inquiry form must never log raw personal data; this rule makes the intent explicit.
      "no-console": ["error", { allow: ["error", "warn"] }]
    }
  }
];

export default eslintConfig;
