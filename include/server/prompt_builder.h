#pragma once

#include "memory/database.h"
#include <string>
#include <vector>

namespace solus {

class PromptBuilder {
public:
  enum class EPromptFormat { QWEN };

  PromptBuilder() = default;

  std::string build_chat_prompt(const std::string &user_message,
                                const std::vector<MemoryEntry> &memories,
                                EPromptFormat format) const;

  static void set_system_prompt(std::string prompt);
};
} // namespace solus