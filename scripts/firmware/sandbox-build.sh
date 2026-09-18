#!/bin/bash
set -euo pipefail
trap 'exit 66' ERR
umask 077
ulimit -S -t 5
ulimit -S -v 524288
ulimit -S -f 2048
ulimit -S -u 32
ulimit -S -n 64
ulimit -S -c 0

export LC_ALL=C
export LANG=C
export TZ=UTC
export SOURCE_DATE_EPOCH=0
export PATH=/toolchain/bin

/bin/mkdir -p /work/obj/core /work/obj/user /work/result

COMMON=(-mmcu=atmega328p -DF_CPU=16000000L -DARDUINO=10819 -DARDUINO_AVR_UNO -DARDUINO_ARCH_AVR -Os -ffunction-sections -fdata-sections -fno-common -fdebug-prefix-map=/work=. -frandom-seed=craftonica-avr-uno-1 -I/source -I/core/cores/arduino -I/core/variants/standard)
CXX=(-std=gnu++11 -fpermissive -fno-exceptions -fno-rtti -fno-threadsafe-statics -Wno-error=narrowing)
C=(-std=gnu11)

index=0
for source in /core/cores/arduino/*.c /core/cores/arduino/*.cpp /core/cores/arduino/*.S; do
    [[ -f "$source" ]] || continue
    object="/work/obj/core/$index.o"
    case "$source" in
        *.c) /toolchain/bin/avr-gcc "${COMMON[@]}" "${C[@]}" -c "$source" -o "$object" ;;
        *.cpp) /toolchain/bin/avr-g++ "${COMMON[@]}" "${CXX[@]}" -c "$source" -o "$object" ;;
        *.S) /toolchain/bin/avr-gcc "${COMMON[@]}" -x assembler-with-cpp -c "$source" -o "$object" ;;
    esac
    index=$((index + 1))
done
for source in /core/variants/standard/*.c /core/variants/standard/*.cpp /core/variants/standard/*.S; do
    [[ -f "$source" ]] || continue
    object="/work/obj/core/$index.o"
    case "$source" in
        *.c) /toolchain/bin/avr-gcc "${COMMON[@]}" "${C[@]}" -c "$source" -o "$object" ;;
        *.cpp) /toolchain/bin/avr-g++ "${COMMON[@]}" "${CXX[@]}" -c "$source" -o "$object" ;;
        *.S) /toolchain/bin/avr-gcc "${COMMON[@]}" -x assembler-with-cpp -c "$source" -o "$object" ;;
    esac
    index=$((index + 1))
done
/toolchain/bin/avr-ar rcsD /work/obj/core.a /work/obj/core/*.o
/toolchain/bin/avr-gcc "${COMMON[@]}" -x assembler-with-cpp -c /runner/craftonica_abi.S -o /work/obj/abi.o

user_objects=()
index=0
while IFS= read -r name; do
    [[ "$name" =~ ^[A-Za-z][A-Za-z0-9_.-]{0,63}$ ]] || exit 65
    source="/source/$name"
    case "$name" in
        *.c) object="/work/obj/user/$index.o"; /toolchain/bin/avr-gcc "${COMMON[@]}" "${C[@]}" -c "$source" -o "$object" ;;
        *.cpp) object="/work/obj/user/$index.o"; /toolchain/bin/avr-g++ "${COMMON[@]}" "${CXX[@]}" -c "$source" -o "$object" ;;
        *.h) continue ;;
        *) exit 65 ;;
    esac
    user_objects+=("$object")
    index=$((index + 1))
done < /source/manifest

/toolchain/bin/avr-g++ -mmcu=atmega328p -Os -Wl,--gc-sections,-Map,/work/result/firmware.map -o /work/result/firmware.elf "${user_objects[@]}" /work/obj/abi.o /work/obj/core.a -lm
/toolchain/bin/avr-strip --strip-all /work/result/firmware.elf
/toolchain/bin/avr-objcopy -O ihex -R .eeprom /work/result/firmware.elf /work/result/firmware.hex
/toolchain/bin/avr-size -A /work/result/firmware.elf

/bin/cp /work/result/firmware.elf /out/firmware.elf
/bin/cp /work/result/firmware.hex /out/firmware.hex
/bin/cp /work/result/firmware.map /out/firmware.map
