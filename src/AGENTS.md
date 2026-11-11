# Source conventions

- Follow the [SonarSource Java style](https://github.com/SonarSource/sonar-developer-toolset#code-style) and surrounding code. Java uses two-space indentation; use one blank line between members and logical sections.
- Import types instead of fully qualifying them inline. Group constants by visibility and keep related constants together. Remove unused imports and accidental consecutive blank lines.
- Include the SSAL header from [HEADER](../HEADER) in every Java file, including tests and package descriptors. Add `package-info.java` for a new package.
- Use the repository's nullability annotations and records for data carriers. Prefer typed domain objects over maps or `Object` when the contract is known.
- Keep methods and classes responsible for one concern. Reuse existing helpers; extract cohesive behavior rather than growing target-switching methods or introducing one-use generic frameworks.
- Comments explain non-obvious reasons or constraints; avoid narrating the code.
- Spotless currently checks license headers, not the complete Java style. Review formatting, imports, naming, and structure yourself; do not reformat unrelated files.
