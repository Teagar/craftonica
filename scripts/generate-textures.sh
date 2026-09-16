#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BLOCKS="$ROOT/src/main/resources/assets/craftonica/textures/blocks"
ITEMS="$ROOT/src/main/resources/assets/craftonica/textures/items"
mkdir -p "$BLOCKS" "$ITEMS"

pixel() {
    magick -size 16x16 "$@" -filter point -define png:color-type=6 PNG32:"$OUTPUT"
}

OUTPUT="$BLOCKS/electrical_wire.png" pixel xc:'#17343a' +antialias -fill '#24535a' -draw 'rectangle 1,1 14,14' -fill '#2d6b70' -draw 'rectangle 2,2 13,5 rectangle 2,10 13,13' -fill '#b86b32' -draw 'rectangle 6,0 9,15 rectangle 0,6 15,9' -fill '#f0a04b' -draw 'rectangle 7,0 8,15 rectangle 0,7 15,8'
OUTPUT="$BLOCKS/power_source.png" pixel xc:'#262d31' +antialias -fill '#414b50' -draw 'rectangle 1,1 14,14' -fill '#58666a' -draw 'rectangle 2,2 13,4' -fill '#d49a2a' -draw 'rectangle 3,6 12,12' -fill '#ffe06a' -draw 'rectangle 7,7 8,11 rectangle 5,9 10,10'
OUTPUT="$BLOCKS/ground.png" pixel xc:'#20262a' +antialias -fill '#354047' -draw 'rectangle 1,1 14,14' -fill '#53616a' -draw 'rectangle 2,2 13,4' -fill '#7ed6c2' -draw 'rectangle 7,6 8,9 rectangle 4,10 11,10 rectangle 5,12 10,12 rectangle 6,14 9,14'
OUTPUT="$BLOCKS/terminal_positive.png" pixel xc:'#2a3033' +antialias -fill '#434d51' -draw 'rectangle 1,1 14,14' -fill '#a65535' -draw 'rectangle 3,3 12,12' -fill '#e58049' -draw 'rectangle 5,5 10,10' -fill '#ffe2a0' -draw 'rectangle 7,6 8,9 rectangle 6,7 9,8'
OUTPUT="$BLOCKS/terminal_neutral.png" pixel xc:'#2a3033' +antialias -fill '#434d51' -draw 'rectangle 1,1 14,14' -fill '#82502f' -draw 'rectangle 3,3 12,12' -fill '#d28a45' -draw 'rectangle 5,5 10,10' -fill '#f1bd69' -draw 'rectangle 6,7 9,8'
OUTPUT="$BLOCKS/terminal_ground.png" pixel xc:'#252d31' +antialias -fill '#3e4a50' -draw 'rectangle 1,1 14,14' -fill '#356a66' -draw 'rectangle 3,3 12,12' -fill '#70b8a8' -draw 'rectangle 5,5 10,10' -fill '#d4eee2' -draw 'rectangle 7,5 8,7 rectangle 5,8 10,8 rectangle 6,10 9,10'
OUTPUT="$BLOCKS/button_open.png" pixel xc:'#263036' +antialias -fill '#40505a' -draw 'rectangle 1,1 14,14' -fill '#647780' -draw 'rectangle 2,2 13,4' -fill '#b83b3b' -draw 'rectangle 5,7 10,12' -fill '#ed6660' -draw 'rectangle 6,6 9,9' -fill '#1a2024' -draw 'rectangle 3,13 12,14'
OUTPUT="$BLOCKS/button_closed.png" pixel xc:'#263036' +antialias -fill '#40505a' -draw 'rectangle 1,1 14,14' -fill '#647780' -draw 'rectangle 2,2 13,4' -fill '#318c57' -draw 'rectangle 5,9 10,12' -fill '#63c77c' -draw 'rectangle 6,8 9,10' -fill '#d7f09b' -draw 'rectangle 3,13 12,14'

resistor() {
    local name="$1" band1="$2" band2="$3" band3="$4"
    OUTPUT="$BLOCKS/$name.png" pixel xc:'#263036' +antialias -fill '#40505a' -draw 'rectangle 1,1 14,14' -fill '#c99a5b' -draw 'rectangle 2,5 13,11' -fill '#edc77d' -draw 'rectangle 3,6 12,9' -fill "$band1" -draw 'rectangle 4,5 5,11' -fill "$band2" -draw 'rectangle 7,5 8,11' -fill "$band3" -draw 'rectangle 10,5 11,11' -fill '#edd36b' -draw 'rectangle 12,5 12,11'
}
resistor resistor_220 '#c83232' '#c83232' '#7a3e25'
resistor resistor_1k '#7a3e25' '#17191a' '#c83232'
resistor resistor_10k '#7a3e25' '#17191a' '#e59a2f'

OUTPUT="$BLOCKS/led_off.png" pixel xc:'#273238' +antialias -fill '#40515a' -draw 'rectangle 1,1 14,14' -fill '#71333a' -draw 'rectangle 4,4 11,12' -fill '#a84a4d' -draw 'rectangle 5,3 10,10' -fill '#d46b68' -draw 'rectangle 6,4 8,6' -fill '#aeb7ad' -draw 'rectangle 5,13 6,15 rectangle 9,13 10,15'
OUTPUT="$BLOCKS/led_on.png" pixel xc:'#443427' +antialias -fill '#72552d' -draw 'rectangle 1,1 14,14' -fill '#b15a30' -draw 'rectangle 3,3 12,12' -fill '#f08a3c' -draw 'rectangle 4,2 11,11' -fill '#ffd86b' -draw 'rectangle 5,3 10,9' -fill '#fff3a5' -draw 'rectangle 6,4 8,6'
OUTPUT="$BLOCKS/led_burned.png" pixel xc:'#1e2224' +antialias -fill '#353b3d' -draw 'rectangle 1,1 14,14' -fill '#3b2827' -draw 'rectangle 4,4 11,12' -fill '#181617' -draw 'rectangle 5,3 10,10' -fill '#69504a' -draw 'rectangle 6,4 7,5 rectangle 9,7 10,8' -fill '#77736d' -draw 'rectangle 5,13 6,15 rectangle 9,13 10,15'
OUTPUT="$BLOCKS/led_anode.png" pixel xc:'#2b3337' +antialias -fill '#a9473f' -draw 'rectangle 2,2 13,13' -fill '#ef7662' -draw 'rectangle 4,4 11,11' -fill '#fff0be' -draw 'rectangle 7,5 8,10 rectangle 5,7 10,8'
OUTPUT="$BLOCKS/led_cathode.png" pixel xc:'#2b3337' +antialias -fill '#315d78' -draw 'rectangle 2,2 13,13' -fill '#5695ad' -draw 'rectangle 4,4 11,11' -fill '#d7f0ef' -draw 'rectangle 5,7 10,8'

OUTPUT="$ITEMS/multimeter.png" pixel xc:none +antialias -fill '#172329' -draw 'rectangle 3,1 12,14 rectangle 2,3 13,12' -fill '#d59a2f' -draw 'rectangle 3,2 12,12' -fill '#f0bc48' -draw 'rectangle 4,2 11,4' -fill '#9dd8c8' -draw 'rectangle 5,4 10,7' -fill '#24454b' -draw 'rectangle 6,5 9,6' -fill '#30373a' -draw 'rectangle 5,9 10,12' -fill '#d94b45' -draw 'rectangle 6,10 7,11' -fill '#32383b' -draw 'rectangle 9,10 10,11' -fill '#d94b45' -draw 'rectangle 3,13 4,15' -fill '#202629' -draw 'rectangle 11,13 12,15'

echo "Texturas 16x16 geradas em assets/craftonica/textures"
