#!/bin/bash
# ============================================================================
# .claude CLI - Unified command runner for all skills and scripts
# ============================================================================
# Usage:
#   .claude/cli.sh <command> [arguments...]
#   .claude/cli.sh help
#   .claude/cli.sh list
#
# Examples:
#   .claude/cli.sh compress-image app/src/main/res/drawable
#   .claude/cli.sh build-debug
#   .claude/cli.sh clean
#   .claude/cli.sh help compress-image
# ============================================================================

set -euo pipefail

CLI_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPTS_DIR="${CLI_DIR}/scripts"
SKILLS_DIR="${CLI_DIR}/skills"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# ============================================================================
# Utility functions
# ============================================================================

print_header() {
    echo -e "${BOLD}${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${BLUE}║           .claude CLI Command Runner             ║${NC}"
    echo -e "${BOLD}${BLUE}╚══════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_separator() {
    echo -e "${BLUE}──────────────────────────────────────────────────${NC}"
}

# Parse SKILL.md frontmatter to extract metadata
parse_skill_meta() {
    local skill_file="$1"
    local field="$2"
    # Extract value between --- markers
    sed -n '/^---$/,/^---$/p' "$skill_file" | grep "^${field}:" | sed "s/^${field}:[[:space:]]*//"
}

# ============================================================================
# Discover available commands
# ============================================================================

# List all script commands (from .claude/scripts/)
list_scripts() {
    if [ -d "$SCRIPTS_DIR" ]; then
        for script in "$SCRIPTS_DIR"/*.sh; do
            [ -f "$script" ] || continue
            local name
            name=$(basename "$script" .sh)
            local desc
            desc=$(head -3 "$script" | grep "^#" | grep -v "^#!" | head -1 | sed 's/^#[[:space:]]*//')
            echo -e "  ${GREEN}${name}${NC}  ${desc:-(no description)}"
        done
    fi
}

# List all skill commands (from .claude/skills/*)
list_skills() {
    if [ -d "$SKILLS_DIR" ]; then
        for skill_dir in "$SKILLS_DIR"/*/; do
            [ -d "$skill_dir" ] || continue
            local name
            name=$(basename "$skill_dir")
            local skill_md="${skill_dir}SKILL.md"
            local desc=""
            local hint=""
            if [ -f "$skill_md" ]; then
                desc=$(parse_skill_meta "$skill_md" "description")
                hint=$(parse_skill_meta "$skill_md" "argument-hint")
            fi
            if [ -n "$hint" ]; then
                echo -e "  ${CYAN}${name}${NC} ${YELLOW}${hint}${NC}"
            else
                echo -e "  ${CYAN}${name}${NC}"
            fi
            if [ -n "$desc" ]; then
                echo -e "    ${desc}"
            fi
        done
    fi
}

# ============================================================================
# Resolve and execute a command
# ============================================================================

resolve_command() {
    local cmd="$1"

    # 1. Check if it's a skill in .claude/skills/<cmd>/ (skills take priority)
    if [ -d "${SKILLS_DIR}/${cmd}" ]; then
        # Look for scripts/compress.sh, scripts/run.sh, scripts/<cmd>.sh, or any single .sh
        local skill_script=""
        for candidate in \
            "${SKILLS_DIR}/${cmd}/scripts/run.sh" \
            "${SKILLS_DIR}/${cmd}/scripts/${cmd}.sh" \
            "${SKILLS_DIR}/${cmd}/scripts/compress.sh"; do
            if [ -f "$candidate" ]; then
                skill_script="$candidate"
                break
            fi
        done

        # Fallback: find any .sh in scripts/
        if [ -z "$skill_script" ] && [ -d "${SKILLS_DIR}/${cmd}/scripts" ]; then
            local count
            count=$(find "${SKILLS_DIR}/${cmd}/scripts" -maxdepth 1 -name "*.sh" | wc -l | tr -d ' ')
            if [ "$count" -eq 1 ]; then
                skill_script=$(find "${SKILLS_DIR}/${cmd}/scripts" -maxdepth 1 -name "*.sh" | head -1)
            fi
        fi

        if [ -n "$skill_script" ]; then
            echo "skill:${skill_script}"
            return 0
        fi
    fi

    # 2. Check if it's a script in .claude/scripts/
    if [ -f "${SCRIPTS_DIR}/${cmd}.sh" ]; then
        echo "script:${SCRIPTS_DIR}/${cmd}.sh"
        return 0
    fi

    return 1
}

run_command() {
    local cmd="$1"
    shift
    local args=("$@")

    local resolved
    resolved=$(resolve_command "$cmd") || {
        echo -e "${RED}Error: Unknown command '${cmd}'${NC}"
        echo ""
        echo "Run '.claude/cli.sh list' to see available commands."
        exit 1
    }

    local type="${resolved%%:*}"
    local path="${resolved#*:}"

    # Ensure executable
    chmod +x "$path" 2>/dev/null || true

    echo -e "${BOLD}Running: ${GREEN}${cmd}${NC} ${args[*]:-}"
    print_separator
    echo ""

    # Execute
    bash "$path" "${args[@]}"
}

# ============================================================================
# Show help for a specific command
# ============================================================================

show_command_help() {
    local cmd="$1"

    local resolved
    resolved=$(resolve_command "$cmd") || {
        echo -e "${RED}Error: Unknown command '${cmd}'${NC}"
        exit 1
    }

    local type="${resolved%%:*}"
    local path="${resolved#*:}"

    if [ "$type" = "skill" ]; then
        local skill_dir
        skill_dir=$(echo "$path" | sed "s|/scripts/.*||")
        local skill_md="${skill_dir}/SKILL.md"
        local ref_md="${skill_dir}/reference.md"

        echo -e "${BOLD}${CYAN}Skill: ${cmd}${NC}"
        print_separator

        if [ -f "$skill_md" ]; then
            local desc=$(parse_skill_meta "$skill_md" "description")
            local hint=$(parse_skill_meta "$skill_md" "argument-hint")
            echo -e "${BOLD}Description:${NC} ${desc}"
            [ -n "$hint" ] && echo -e "${BOLD}Usage:${NC}      .claude/cli.sh ${cmd} ${YELLOW}${hint}${NC}"
            echo ""
        fi

        if [ -f "$ref_md" ]; then
            echo -e "${BOLD}Reference:${NC}"
            cat "$ref_md"
        fi
    else
        echo -e "${BOLD}${GREEN}Script: ${cmd}${NC}"
        print_separator
        echo -e "${BOLD}Location:${NC} ${path}"
        echo ""
        echo -e "${BOLD}Content:${NC}"
        cat "$path"
    fi
}

# ============================================================================
# Interactive mode
# ============================================================================

interactive_mode() {
    print_header
    echo -e "Type a command, or ${YELLOW}'help'${NC} / ${YELLOW}'list'${NC} / ${YELLOW}'quit'${NC}"
    echo ""

    while true; do
        echo -ne "${BOLD}${GREEN}claude>${NC} "
        read -r input || break

        # Trim whitespace
        input=$(echo "$input" | xargs)

        [ -z "$input" ] && continue

        case "$input" in
            quit|exit|q)
                echo -e "${BLUE}Bye!${NC}"
                break
                ;;
            help)
                show_help
                ;;
            list)
                show_list
                ;;
            help\ *)
                local topic="${input#help }"
                show_command_help "$topic"
                ;;
            *)
                # Split input into command and args
                local cmd
                cmd=$(echo "$input" | awk '{print $1}')
                local args
                args=$(echo "$input" | sed "s/^${cmd}[[:space:]]*//" )

                if [ -n "$args" ]; then
                    run_command "$cmd" $args
                else
                    run_command "$cmd"
                fi
                ;;
        esac
        echo ""
    done
}

# ============================================================================
# Help & List
# ============================================================================

show_list() {
    echo -e "${BOLD}📜 Available Scripts:${NC} ${YELLOW}(.claude/scripts/)${NC}"
    list_scripts
    echo ""
    echo -e "${BOLD}🧩 Available Skills:${NC} ${YELLOW}(.claude/skills/)${NC}"
    list_skills
}

show_help() {
    print_header
    echo -e "${BOLD}Usage:${NC}"
    echo "  .claude/cli.sh <command> [arguments...]"
    echo "  .claude/cli.sh list                        List all commands"
    echo "  .claude/cli.sh help <command>              Show help for a command"
    echo "  .claude/cli.sh interactive                 Enter interactive mode"
    echo ""
    show_list
    echo ""
    echo -e "${BOLD}Examples:${NC}"
    echo "  .claude/cli.sh compress-image app/src/main/res/drawable"
    echo "  .claude/cli.sh build-debug"
    echo "  .claude/cli.sh clean"
    echo "  .claude/cli.sh help compress-image"
}

# ============================================================================
# Main entry point
# ============================================================================

main() {
    if [ $# -eq 0 ]; then
        show_help
        exit 0
    fi

    local cmd="$1"
    shift

    case "$cmd" in
        help|-h|--help)
            if [ $# -gt 0 ]; then
                show_command_help "$1"
            else
                show_help
            fi
            ;;
        list|-l|--list)
            show_list
            ;;
        interactive|-i|--interactive)
            interactive_mode
            ;;
        *)
            run_command "$cmd" "$@"
            ;;
    esac
}

main "$@"

