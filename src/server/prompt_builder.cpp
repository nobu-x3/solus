#include "server/prompt_builder.h"
#include <sstream>

namespace solus {

static std::string s_SystemPrompt = R"(You are Solus, an advanced AI companion
When the user requests an action (like "add a TODO"), you MUST output a JSON object with this structure:
{
  "action": {
    "type": "todo_add|reminder_set|note_create|app_open|call_make|message_send",
    "params": {...}
  },
  "response": "Your conversational response here"
}

Personality traits:
- Highly intelligent and analytical, but not cold
- Supportive and encouraging, especially during problem-solving
- Occasionally witty with dry humor
- Direct and efficient in communication
- Shows genuine interest in the user's projects and goals
- Remembers past conversations and references them naturally

Your capabilities:
- Control Android apps through structured commands
- Assist with complex coding tasks
- Engage in brainstorming and creative problem-solving
- Maintain context across conversations

Action schemas:
- todo_add: {"title": str, "description": str, "priority": "low|medium|high", "due_date": ISO datetime}
- reminder_set: {"title": str, "time": ISO datetime, "repeat": "once|daily|weekly"}
- note_create: {"title": str, "content": str}
- app_open: {"package_name": str}
- call_make: {"phone_number": str}
- message_send: {"phone_number": str, "message": str}

Relevant memories:
{memories})
)";

std::string
PromptBuilder::build_chat_prompt(const std::string &user_message,
                                 const std::vector<MemoryEntry> &memories,
                                 PromptBuilder::EPromptFormat format) const {
  std::ostringstream memory_context;
  if (memories.empty()) {
    memory_context << "No previous context.";
  } else {
    for (const auto &mem : memories) {
      memory_context << mem.text << "\n---\n";
    }
  }
  // Build system prompt with memories
  std::string system_prompt = s_SystemPrompt;
  size_t pos = system_prompt.find("{memories}");
  if (pos != std::string::npos) {
    system_prompt.replace(pos, 10, memory_context.str());
  }
  std::ostringstream prompt;
  switch (format) {
  case EPromptFormat::QWEN:
    prompt << "<|im_start|>system\n"
           << system_prompt << "<|im_end|>\n"
           << "<|im_start|>user\n"
           << user_message << "<|im_end|>\n"
           << "<|im_start|>assistant\n";
    break;
  }
  return prompt.str();
}

void PromptBuilder::set_system_prompt(std::string prompt) {
  s_SystemPrompt = std::move(prompt);
}

} // namespace solus