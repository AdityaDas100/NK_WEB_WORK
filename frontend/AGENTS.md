<!-- BEGIN:nextjs-agent-rules -->

## This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

## Code Quality & Engineering Standards

You must strictly adhere to the following software development principles for all code generation, refactoring, and architectural tasks:

### 1. Architectural Foundations (SOLID)
* **Single Responsibility:** Every class, module, service, and function must have exactly one reason to change. Split monolithic logic into focused, decoupled components.
* **Open/Closed:** Code must be open for extension but closed for modification. Leverage interfaces, abstract classes, and polymorphism instead of rigid conditional branches (`if/else` or `switch` over types).
* **Liskov Substitution:** Subtypes must be completely substitutable for their base types without altering system correctness.
* **Interface Segregation:** Clients must not be forced to depend on methods or elements they do not use. Keep interfaces thin, cohesive, and task-specific.
* **Dependency Inversion:** High-level business logic must depend on abstractions (interfaces), not low-level concrete implementations. Inject dependencies dynamically.

### 2. Scalability & Maintainability
* **Design for Growth:** Write stateless logic where possible to support horizontal scaling. Ensure database queries leverage optimal indexing patterns and avoid N+1 query problems.
* **Self-Documenting Code:** Prioritize expressive naming conventions (variables, functions, classes) that state *intent* over obscure abbreviations.
* **Modularity:** Package components cleanly by feature or domain, ensuring that layers (e.g., Controllers, Services, Repositories) maintain strict separation of concerns.

### 3. User Experience (UX) & Resilient Error Handling
* **Graceful Degradation:** Never allow an unhandled exception or raw system stack trace to escape to the client interface. Catch errors early and safely.
* **Informative Responses:** Map server-side exceptions to semantic HTTP status codes accompanied by localized, user-friendly, and actionable error payloads (strictly hiding sensitive system details).
* **Frontend Fault-Tolerance:** Utilize explicit React Error Boundaries and robust catch blocks in Next.js Server Actions to display meaningful fallback UIs instead of a blank screen or a generic crash page.

## Code Quality & Engineering Standards

You must strictly adhere to the following software development principles for all code generation, refactoring, and architectural tasks:

### 1. Architectural Foundations (SOLID)
* **Single Responsibility:** Every class, module, service, and function must have exactly one reason to change. Split monolithic logic into focused, decoupled components.
* **Open/Closed:** Code must be open for extension but closed for modification. Leverage interfaces, abstract classes, and polymorphism instead of rigid conditional branches (`if/else` or `switch` over types).
* **Liskov Substitution:** Subtypes must be completely substitutable for their base types without altering system correctness.
* **Interface Segregation:** Clients must not be forced to depend on methods or elements they do not use. Keep interfaces thin, cohesive, and task-specific.
* **Dependency Inversion:** High-level business logic must depend on abstractions (interfaces), not low-level concrete implementations. Inject dependencies dynamically.

### 2. Scalability & Maintainability
* **Design for Growth:** Write stateless logic where possible to support horizontal scaling. Ensure database queries leverage optimal indexing patterns and avoid N+1 query problems.
* **Self-Documenting Code:** Prioritize expressive naming conventions (variables, functions, classes) that state *intent* over obscure abbreviations.
* **Modularity:** Package components cleanly by feature or domain, ensuring that layers (e.g., Controllers, Services, Repositories) maintain strict separation of concerns.

### 3. User Experience (UX) & Resilient Error Handling
* **Graceful Degradation:** Never allow an unhandled exception or raw system stack trace to escape to the client interface. Catch errors early and safely.
* **Informative Responses:** Map server-side exceptions to semantic HTTP status codes accompanied by localized, user-friendly, and actionable error payloads (strictly hiding sensitive system details).
* **Frontend Fault-Tolerance:** Utilize explicit React Error Boundaries and robust catch blocks in Next.js Server Actions to display meaningful fallback UIs instead of a blank screen or a generic crash page.




