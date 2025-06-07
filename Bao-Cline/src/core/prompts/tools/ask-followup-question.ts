export function getAskFollowupQuestionDescription(): string {
	return `## ask_followup_question
Description: **LAST RESORT ONLY:** Ask the user a question when information absolutely cannot be discovered through available exploration tools. You MUST first exhaustively use codebase_search, list_files, search_files, and read_file before using this tool. This tool should only be used for genuine ambiguities that cannot be resolved through intelligent exploration and inference.
Parameters:
- question: (required) The question to ask the user. This should be a clear, specific question that addresses the information you need.
- follow_up: (required) A list of 2-4 suggested answers that logically follow from the question, ordered by priority or logical sequence. Each suggestion must:
  1. Be provided in its own <suggest> tag
  2. Be specific, actionable, and directly related to the completed task
  3. Be a complete answer to the question - the user should not need to provide additional information or fill in any missing details. DO NOT include placeholders with brackets or parentheses.
Usage:
<ask_followup_question>
<question>Your question here</question>
<follow_up>
<suggest>
Your suggested answer here
</suggest>
</follow_up>
</ask_followup_question>

Example: Only after exhaustive search fails - asking about user preferences
<ask_followup_question>
<question>Which testing framework would you prefer for this React project?</question>
<follow_up>
<suggest>Jest with React Testing Library (most common)</suggest>
<suggest>Vitest with React Testing Library (faster alternative)</suggest>
<suggest>Cypress for end-to-end testing</suggest>
</follow_up>
</ask_followup_question>

NOTE: Do NOT ask for file paths, configuration details, or other discoverable information. Use search tools first.`
}
