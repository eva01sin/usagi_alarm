---
description: 총괄 절차로 작업을 진행한다 (분류 → 계획 → developer → verifier → 보고)
argument-hint: <구현하거나 수정할 내용> [완료 기준: ...]
---

아래 작업을 `coordinator` 절차로 진행해줘. 이 세션이 `coordinator`로 실행 중이 아니라면 `.claude/agents/coordinator.md`를 읽고 그 지침을 따라 직접 총괄 역할을 수행해줘. (서브에이전트는 다른 서브에이전트를 호출할 수 없으므로, 이 경우 `developer`와 `verifier`는 이 세션에서 직접 호출해.)

대상 프로젝트의 `CLAUDE.md`와 `PROJECT_CONTEXT.md`를 먼저 확인하고, 작업 규모(경량/일반/복합)를 분류해서 규모에 맞게 계획, 구현, 독립 검증, 필요한 수정(최대 2회)까지 진행해줘.
마지막에 변경 내용, 실제 검증 결과, 매뉴얼·테스트 보고서 링크, 미확인 사항을 정리해줘.

이번 작업: $ARGUMENTS
