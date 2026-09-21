#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BLOCKS="$ROOT/src/main/resources/assets/craftonica/textures/blocks"
ITEMS="$ROOT/src/main/resources/assets/craftonica/textures/items"
ENTITIES="$ROOT/src/main/resources/assets/craftonica/textures/entity"
mkdir -p "$BLOCKS" "$ITEMS" "$ENTITIES"

pixel() {
    magick -size 16x16 "$@" -filter point -strip -define png:color-type=6 PNG32:"$OUTPUT"
}

OUTPUT="$BLOCKS/electrical_wire.png" pixel xc:'#737b7b' +antialias -fill '#aeb7b5' -draw 'rectangle 1,1 14,14' -fill '#dce3df' -draw 'rectangle 2,2 13,5 rectangle 2,10 13,13' -fill '#f4f7f3' -draw 'rectangle 6,0 9,15 rectangle 0,6 15,9' -fill '#ffffff' -draw 'rectangle 7,0 8,15 rectangle 0,7 15,8'
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

OUTPUT="$BLOCKS/led_anode.png" pixel xc:'#2b3337' +antialias -fill '#a9473f' -draw 'rectangle 2,2 13,13' -fill '#ef7662' -draw 'rectangle 4,4 11,11' -fill '#fff0be' -draw 'rectangle 7,5 8,10 rectangle 5,7 10,8'
OUTPUT="$BLOCKS/led_cathode.png" pixel xc:'#2b3337' +antialias -fill '#315d78' -draw 'rectangle 2,2 13,13' -fill '#5695ad' -draw 'rectangle 4,4 11,11' -fill '#d7f0ef' -draw 'rectangle 5,7 10,8'
OUTPUT="$BLOCKS/diode_off.png" pixel xc:'#273238' +antialias -fill '#6e7c83' -draw 'rectangle 2,4 13,11' -fill '#aebbc0' -draw 'rectangle 4,5 11,10' -fill '#e2f0ed' -draw 'rectangle 8,5 9,10'
OUTPUT="$BLOCKS/diode_anode.png" pixel xc:'#2b3337' +antialias -fill '#a9473f' -draw 'rectangle 2,2 13,13' -fill '#ef7662' -draw 'rectangle 4,4 11,11' -fill '#fff0be' -draw 'rectangle 7,5 8,10'
OUTPUT="$BLOCKS/diode_cathode.png" pixel xc:'#2b3337' +antialias -fill '#315d78' -draw 'rectangle 2,2 13,13' -fill '#5695ad' -draw 'rectangle 4,4 11,11' -fill '#d7f0ef' -draw 'rectangle 5,7 10,8'
OUTPUT="$BLOCKS/lever.png" pixel xc:'#252d31' +antialias -fill '#4e5d63' -draw 'rectangle 2,5 13,13' -fill '#9a713d' -draw 'rectangle 7,1 9,11' -fill '#d7a35b' -draw 'rectangle 6,1 10,4'
OUTPUT="$BLOCKS/roboboard.png" pixel xc:'#17272b' +antialias -fill '#24584e' -draw 'rectangle 1,1 14,14' -fill '#347765' -draw 'rectangle 2,2 13,13' -fill '#c9b66b' -draw 'rectangle 1,3 2,4 rectangle 1,7 2,8 rectangle 1,11 2,12 rectangle 13,3 14,4 rectangle 13,7 14,8 rectangle 13,11 14,12' -fill '#182126' -draw 'rectangle 5,4 10,10' -fill '#435058' -draw 'rectangle 6,5 9,9' -fill '#df9d35' -draw 'rectangle 11,2 12,3' -fill '#d9574f' -draw 'rectangle 11,12 12,13'
OUTPUT="$BLOCKS/robot_chassis.png" pixel xc:'#172329' +antialias -fill '#263b42' -draw 'rectangle 1,1 14,14' -fill '#365761' -draw 'rectangle 2,2 13,12' -fill '#4d7480' -draw 'rectangle 3,3 12,5' -fill '#132026' -draw 'rectangle 2,13 13,14 rectangle 3,7 5,11 rectangle 10,7 12,11' -fill '#d79a32' -draw 'polygon 7,6 4,10 6,10 6,13 9,13 9,10 11,10'
OUTPUT="$BLOCKS/h_bridge.png" pixel xc:'#152226' +antialias -fill '#244b43' -draw 'rectangle 1,1 14,14' -fill '#347264' -draw 'rectangle 2,2 13,13' -fill '#151a1d' -draw 'rectangle 5,4 10,11' -fill '#465057' -draw 'rectangle 6,5 9,10' -fill '#c8ad62' -draw 'rectangle 1,3 3,4 rectangle 1,7 3,8 rectangle 1,11 3,12 rectangle 12,3 14,4 rectangle 12,7 14,8 rectangle 12,11 14,12' -fill '#d95a48' -draw 'rectangle 3,2 4,3' -fill '#62b7a2' -draw 'rectangle 11,12 12,13'

magick -size 64x64 xc:'#2a4650' +antialias \
  -fill '#365b66' -draw 'rectangle 0,0 35,22' \
  -fill '#4e7a84' -draw 'rectangle 1,1 34,8' \
  -fill '#1a2428' -draw 'rectangle 52,0 63,22' \
  -fill '#101619' -draw 'rectangle 54,2 61,20' \
  -fill '#285f51' -draw 'rectangle 0,23 35,38' \
  -fill '#3b806c' -draw 'rectangle 2,25 33,36' \
  -fill '#172125' -draw 'rectangle 0,39 35,51' \
  -fill '#4f5d61' -draw 'rectangle 2,41 33,49' \
  -fill '#17372f' -draw 'rectangle 36,39 63,47' \
  -fill '#d0b65e' -draw 'rectangle 38,41 61,45' \
  -fill '#d28f2d' -draw 'rectangle 36,48 63,55' \
  -fill '#244e47' -draw 'rectangle 0,52 23,63' \
  -fill '#65a89a' -draw 'rectangle 2,54 21,61' \
  -fill '#b9c5bf' -draw 'rectangle 24,52 35,63' \
  -fill '#e5ece7' -draw 'rectangle 26,54 33,61' \
  -filter point -strip -define png:color-type=6 PNG32:"$ENTITIES/mobile_robot.png"

OUTPUT="$ITEMS/multimeter.png" pixel xc:none +antialias -fill '#172329' -draw 'rectangle 3,1 12,14 rectangle 2,3 13,12' -fill '#d59a2f' -draw 'rectangle 3,2 12,12' -fill '#f0bc48' -draw 'rectangle 4,2 11,4' -fill '#9dd8c8' -draw 'rectangle 5,4 10,7' -fill '#24454b' -draw 'rectangle 6,5 9,6' -fill '#30373a' -draw 'rectangle 5,9 10,12' -fill '#d94b45' -draw 'rectangle 6,10 7,11' -fill '#32383b' -draw 'rectangle 9,10 10,11' -fill '#d94b45' -draw 'rectangle 3,13 4,15' -fill '#202629' -draw 'rectangle 11,13 12,15'
OUTPUT="$ITEMS/wrench.png" pixel xc:none +antialias -fill '#323b40' -draw 'polygon 2,1 5,1 7,4 5,6 13,14 11,16 3,8 1,9 0,6 2,4' -fill '#98a6aa' -draw 'polygon 3,1 5,2 6,4 4,6 2,5 1,6 2,8 4,7 12,15 13,14 5,6 7,4 5,1' -fill '#d9e0de' -draw 'polygon 3,2 5,3 5,4 4,5 2,4'
OUTPUT="$ITEMS/manual.png" pixel xc:none +antialias -fill '#39291f' -draw 'rectangle 2,1 13,14 rectangle 1,3 14,13' -fill '#be8a43' -draw 'rectangle 3,2 12,13 rectangle 2,4 13,12' -fill '#e5c475' -draw 'rectangle 4,3 11,12' -fill '#4c6f69' -draw 'rectangle 7,4 8,10 rectangle 5,6 10,8' -fill '#f2e2aa' -draw 'rectangle 7,5 8,9 rectangle 6,6 9,8' -fill '#6b3d2b' -draw 'rectangle 2,2 3,13'

echo "Texturas 16x16 geradas em assets/craftonica/textures"
