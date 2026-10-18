---
title: Review major and minor Renovate updates
description: Run a quick advisory dependency safety review for eligible Renovate pull requests.
type: automation
when: >
  A pull request is open, is authored by the actual Renovate bot, and has the
  renovate-major-minor label, including grouped dependency updates.
actions: >
  Run the dependency-safety skill on this pull request. The skill must verify
  that current content still includes a major or minor update and keep one
  advisory summary up to date. A label alone is not evidence of eligibility;
  disclose ambiguous classification as
  Unable to verify rather than assuming the update is major or minor.
---

The dependency-safety skill owns the review scope, evidence checks, and report.
A human can explicitly request a review, including a patch update, with:
`gitar run the dependency-safety skill on this PR`.
