#!/usr/bin/env bash

# Regenerates the local Material Symbols Rounded ImageVectors used by commonMain.
# The vectors come directly from Google's Kotlin endpoint, so the shared UI never
# depends on XML drawables or the deprecated Compose Material Icons artifacts.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/shared/src/commonMain/kotlin/com/bernaferrari/bravedns/ui/icons"
PACKAGE_NAME="com.bernaferrari.bravedns.ui.icons"
BASE_URL="https://fonts.gstatic.com/render/v1/Material+Symbols+Rounded/24dp"

# Keep this list in sync with the commonMain usages. Variants share a source file:
# filled is FILL=1, outlined is FILL=0, and the facade records auto-mirror intent.
readonly FILLED_ICONS=(
  add apps article arrow_back arrow_forward auto_stories backup bar_chart block
  brightness_auto bug_report build business_center calendar_month camera_alt cancel
  cell_tower chat chat_bubble check check_circle clear close cloud code content_copy
  dark_mode delete delete_outline dns download edit edit_location_alt email event_note
  filter_list forum gavel gpp_bad group headset help_outline home http info
  keyboard_arrow_down keyboard_arrow_right language light_mode local_taxi lock map
  mobile_off more_horiz more_vert movie music_note network_check network_ping
  new_releases notifications notifications_off palette phone_android photo_library
  play_arrow policy public radio_button_checked radio_button_unchecked refresh remove
  restart_alt restore search security send settings share shield shield_moon shop
  shopping_cart signal_cellular_alt star star_border stop storage subject system_update
  system_update_alt timer tune vpn_key warning warning_amber wb_sunny wifi wifi_off work
)
# Keep outline counterparts for selection-state components such as the root
# navigation.  Selected destinations use FILL=1; inactive destinations use
# FILL=0 while retaining the same symbol and visual weight.
readonly OUTLINED_ICONS=(home info settings star star_border)
readonly AUTO_MIRRORED_FILLED_ICONS=(
  arrow_back arrow_forward article chat help_outline keyboard_arrow_right send subject
)

CHECK_ONLY=0
if [[ "$#" -gt 0 ]]; then
  case "$1" in
    --check) CHECK_ONLY=1 ;;
    -h|--help)
      printf '%s\n' "Usage: scripts/generate-material-symbols.sh [--check]"
      exit 0
      ;;
    *)
      printf 'Unknown option: %s\n' "$1" >&2
      exit 2
      ;;
  esac
fi

for command in curl grep mkdir mktemp mv perl rm sed sort; do
  command -v "$command" >/dev/null 2>&1 || {
    printf 'Required command not found: %s\n' "$command" >&2
    exit 1
  }
done

to_pascal_case() {
  printf '%s' "$1" | perl -pe 's/(^|_)([a-z0-9])/$2 eq "" ? $1 : uc($2)/ge'
}

download_vector() {
  local icon="$1"
  local fill="$2"
  local output="$3"
  local url="$BASE_URL/$icon.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,$fill,0,50"

  curl --compressed --fail --silent --show-error --location --retry 3 --retry-delay 1 \
    --connect-timeout 15 --max-time 60 --user-agent 'RethinkDNS Material Symbols generator' \
    "$url" --output "$output"
}

normalize_vector() {
  local source="$1"
  local icon="$2"
  local style="$3"
  local pascal
  local prefix
  pascal="$(to_pascal_case "$icon")"
  case "$style" in
    Filled) prefix=filled ;;
    Outlined) prefix=outlined ;;
    *) printf 'Unsupported style: %s\n' "$style" >&2; exit 1 ;;
  esac

  perl -pi -e "s/^package .*/package $PACKAGE_NAME/" "$source"
  perl -pi -e "s/public val $icon\\b/public val $prefix$pascal/g" "$source"
  perl -pi -e "s/_$icon\\b/_$prefix$pascal/g" "$source"
  perl -pi -e 's/^public val /internal val /; s/^private var /internal var /' "$source"

  grep -Fq "internal val $prefix$pascal: ImageVector" "$source" &&
    grep -Fq "internal var _$prefix$pascal: ImageVector? = null" "$source" || {
      printf 'Google response format changed for %s (%s)\n' "$icon" "$style" >&2
      return 1
    }
}

vector_body() {
  local source="$1"
  grep -Fc '@Suppress("CheckReturnValue")' "$source" | grep -qx 1 || {
    printf 'Expected one generated vector declaration in %s\n' "$source" >&2
    return 1
  }
  sed -n '/^@Suppress("CheckReturnValue")/,$p' "$source"
}

has_outlined_variant() {
  local icon="$1"
  local candidate
  for candidate in "${OUTLINED_ICONS[@]}"; do
    [[ "$candidate" == "$icon" ]] && return 0
  done
  return 1
}

generate_icon() {
  local icon="$1"
  local destination_dir="$2"
  local pascal
  local temp_dir
  local output
  pascal="$(to_pascal_case "$icon")"
  temp_dir="$WORK_DIR/downloads/$icon"
  output="$destination_dir/$pascal"Icon.kt
  mkdir -p "$temp_dir"

  download_vector "$icon" 1 "$temp_dir/filled.kt"
  normalize_vector "$temp_dir/filled.kt" "$icon" Filled

  {
    cat <<EOF
package $PACKAGE_NAME

// Generated from Google Material Symbols Rounded's Kotlin vector endpoint.
// FILL=1 is Filled and FILL=0 is Outlined; opsz=24, wght=400, GRAD=0, ROND=50.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

EOF
    vector_body "$temp_dir/filled.kt"
    if has_outlined_variant "$icon"; then
      download_vector "$icon" 0 "$temp_dir/outlined.kt"
      normalize_vector "$temp_dir/outlined.kt" "$icon" Outlined
      printf '\n'
      vector_body "$temp_dir/outlined.kt"
    fi
  } > "$output"
}

generate_facade() {
  local destination_dir="$1"
  local icon
  local pascal
  local output="$destination_dir/MaterialSymbols.kt"

  {
    cat <<EOF
package $PACKAGE_NAME

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material Symbols Rounded vectors generated by scripts/generate-material-symbols.sh.
 *
 * Filled and Outlined map to the Material Symbols FILL axis. AutoMirrored marks directional
 * usages at the call site while retaining an ImageVector-compatible API for common UI.
 */
object MaterialSymbols {
    object Filled {
EOF
    for icon in "${FILLED_ICONS[@]}"; do
      pascal="$(to_pascal_case "$icon")"
      printf '        val %s: ImageVector get() = filled%s\n' "$pascal" "$pascal"
    done
    cat <<'EOF'
    }

    object Outlined {
EOF
    for icon in "${OUTLINED_ICONS[@]}"; do
      pascal="$(to_pascal_case "$icon")"
      printf '        val %s: ImageVector get() = outlined%s\n' "$pascal" "$pascal"
    done
    cat <<'EOF'
    }

    object AutoMirrored {
        object Filled {
EOF
    for icon in "${AUTO_MIRRORED_FILLED_ICONS[@]}"; do
      pascal="$(to_pascal_case "$icon")"
      printf '            val %s: ImageVector get() = filled%s\n' "$pascal" "$pascal"
    done
    cat <<'EOF'
        }
    }
}
EOF
  } > "$output"
}

validate_output() {
  local output_dir="$1"
  local icon
  local pascal
  for icon in "${FILLED_ICONS[@]}"; do
    pascal="$(to_pascal_case "$icon")"
    grep -Fq "internal val filled$pascal" "$output_dir/$pascal"Icon.kt || {
      printf 'Missing filled output for %s\n' "$icon" >&2
      return 1
    }
  done
  [[ -f "$output_dir/MaterialSymbols.kt" ]]
}

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/rethink-material-symbols.XXXXXX")"
STAGING_DIR="$WORK_DIR/output"
cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT
mkdir -p "$STAGING_DIR" "$WORK_DIR/downloads"

for icon in "${FILLED_ICONS[@]}"; do
  generate_icon "$icon" "$STAGING_DIR"
done
generate_facade "$STAGING_DIR"
validate_output "$STAGING_DIR"

if [[ "$CHECK_ONLY" -eq 1 ]]; then
  diff -qr "$STAGING_DIR" "$OUTPUT_DIR"
  printf '%s\n' 'Material Symbols outputs are up to date.'
  exit 0
fi

mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_DIR"/*Icon.kt "$OUTPUT_DIR/MaterialSymbols.kt"
mv "$STAGING_DIR"/*.kt "$OUTPUT_DIR/"
printf 'Generated %s local Material Symbols in %s\n' "${#FILLED_ICONS[@]}" "$OUTPUT_DIR"
