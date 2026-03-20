  # 🚀 Project Contribution Guide
  
  *Note: This document is a living guide and is **subject to change** as our workflow evolves. Significant updates will be announced on the Discord server.*
  
  ## 1. The Golden Rule
  **The `main` branch must always be buildable.** If your code breaks the build or fails the automated checks, it does not get merged.
  
  ## 2. Branching & Workflow
  We use a feature-branch workflow to keep our work organized and isolated.
  
  * **Create a Branch:** Give your branch a descriptive name (e.g., `feat/login-api` or `fix/header-styling`).
  * **Stay Updated:** Frequently pull the latest changes from `main` into your branch using `git rebase main` to minimize merge conflicts.
  * **PR Requirement:** Every Pull Request requires at least one "Approved" review from a teammate and a passing CI build.
  
  ## 3. Code Style & Formatting
  We do not manually debate code style (tabs vs. spaces, brace placement, etc.). We use automated tooling to ensure the entire codebase remains consistent and readable.
  
  * **The Tooling:** TBA
  * **The Command:** TBA
  * **The Enforcement:** The CI pipeline will automatically reject any PR that does not adhere to the project's defined style. 
  
  *If you feel the current style is hindering productivity, bring it up with the DevOps team rather than ignoring the formatter.*
  
  ## 4. How to Submit a Change
  1.  **Sync** your local `main` branch.
  2.  **Create** your feature branch.
  3.  **Code** your changes and add tests where applicable.
  4.  **Format** your code.
  5.  **Push** to GitHub and open a Pull Request.
  6.  **Address** feedback from reviewers and fix any CI failures.
