package br.com.craftonica.firmware;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Parses hostile AVR ELF output without loading or executing it. */
public final class AvrElfVerifier {
    public static final int MAX_ELF_BYTES = 2 * 1024 * 1024;

    private static final int ELF_HEADER_BYTES = 52;
    private static final int PROGRAM_HEADER_BYTES = 32;
    private static final int SECTION_HEADER_BYTES = 40;
    private static final int PT_LOAD = 1;
    private static final int SHT_RELA = 4;
    private static final int SHT_REL = 9;
    private static final long SRAM_START = 0x800100L;
    private static final long SRAM_END = SRAM_START + ToolchainProfile.MAX_SRAM_BYTES;
    private static final Set<String> METADATA_SECTIONS = new HashSet<String>(Arrays.asList(
            "", ".comment", ".note.gnu.avr.deviceinfo", ".note.craftonica.abi", ".shstrtab",
            ".debug_aranges", ".debug_info", ".debug_abbrev", ".debug_line", ".debug_str",
            ".debug_ranges", ".debug_frame", ".debug_loc", ".debug_pubnames", ".debug_pubtypes"));

    public CRLFirmware verify(byte[] elf, byte[] expectedSourceHash) {
        if (elf == null || elf.length < ELF_HEADER_BYTES || elf.length > MAX_ELF_BYTES)
            throw rejected("ELF length is outside the allowed range");
        if (expectedSourceHash == null || expectedSourceHash.length != 32)
            throw new IllegalArgumentException("Expected source hash must contain 32 bytes");

        ByteBuffer input = ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN);
        require(input.get(0) == 0x7f && input.get(1) == 'E' && input.get(2) == 'L' && input.get(3) == 'F',
                "Invalid ELF magic");
        require(u8(input, 4) == 1 && u8(input, 5) == 1 && u8(input, 6) == 1 && u8(input, 7) == 0,
                "Only ELF32 little-endian System V is supported");
        require(u16(input, 16) == 2 && u16(input, 18) == 83 && u32(input, 20) == 1,
                "ELF is not an AVR executable");
        require(u32(input, 24) == 0 && u32(input, 36) == 5, "ELF entry point or AVR architecture is invalid");
        require(u16(input, 40) == ELF_HEADER_BYTES && u16(input, 42) == PROGRAM_HEADER_BYTES,
                "ELF header sizes are invalid");

        long programOffset = u32(input, 28);
        int programCount = u16(input, 44);
        long sectionOffset = u32(input, 32);
        int sectionCount = u16(input, 48);
        int sectionNames = u16(input, 50);
        require(programCount > 0 && programCount <= 8, "ELF program header count is invalid");
        require(sectionCount > 0 && sectionCount <= 64 && sectionNames < sectionCount,
                "ELF section header count is invalid");
        require(u16(input, 46) == SECTION_HEADER_BYTES, "ELF section header size is invalid");
        requireRange(programOffset, (long) programCount * PROGRAM_HEADER_BYTES, elf.length, "program headers");
        requireRange(sectionOffset, (long) sectionCount * SECTION_HEADER_BYTES, elf.length, "section headers");
        require(validateSections(input, elf, sectionOffset, sectionCount, sectionNames),
                "Craftonica ABI note is missing or invalid");

        byte[] flash = new byte[ToolchainProfile.MAX_FLASH_BYTES];
        Arrays.fill(flash, (byte) 0xff);
        boolean[] occupied = new boolean[flash.length];
        long[] virtualStarts = new long[programCount];
        long[] virtualEnds = new long[programCount];
        int flashEnd = 0;
        for (int i = 0; i < programCount; i++) {
            int base = checkedInt(programOffset + (long) i * PROGRAM_HEADER_BYTES);
            require(u32(input, base) == PT_LOAD, "Unknown ELF program header");
            long fileOffset = u32(input, base + 4);
            long virtualAddress = u32(input, base + 8);
            long physicalAddress = u32(input, base + 12);
            long fileSize = u32(input, base + 16);
            long memorySize = u32(input, base + 20);
            long flags = u32(input, base + 24);
            long alignment = u32(input, base + 28);
            virtualStarts[i] = virtualAddress;
            virtualEnds[i] = virtualAddress + memorySize;
            require(fileSize <= memorySize && (alignment == 1 || alignment == 2 || alignment == 4),
                    "Invalid ELF load segment");
            requireRange(fileOffset, fileSize, elf.length, "load segment");
            if (fileSize == 0) {
                require(virtualAddress >= SRAM_START && virtualAddress + memorySize <= SRAM_END,
                        "Memory-only segment is outside SRAM");
                continue;
            }
            require((flags & 2) == 0 || virtualAddress >= SRAM_START,
                    "Writable segment has an invalid virtual address");
            if ((flags & 2) != 0) require(memorySize <= ToolchainProfile.MAX_SRAM_BYTES
                            && virtualAddress <= SRAM_END - memorySize,
                    "Writable segment exceeds SRAM");
            require(physicalAddress < ToolchainProfile.MAX_FLASH_BYTES
                            && physicalAddress + fileSize <= ToolchainProfile.MAX_FLASH_BYTES,
                    "Load image exceeds AVR flash");
            int destination = checkedInt(physicalAddress);
            int length = checkedInt(fileSize);
            for (int j = 0; j < length; j++) {
                require(!occupied[destination + j], "Overlapping ELF load segments");
                occupied[destination + j] = true;
                flash[destination + j] = elf[checkedInt(fileOffset) + j];
            }
            flashEnd = Math.max(flashEnd, destination + length);
        }
        validateAllocatedSectionCoverage(input, sectionOffset, sectionCount, virtualStarts, virtualEnds);
        require(flashEnd > 0 && flashEnd <= ToolchainProfile.MAX_FLASH_BYTES, "ELF contains no flash image");
        if ((flashEnd & 1) != 0) flashEnd++;
        return CRLFirmware.create(expectedSourceHash, Arrays.copyOf(flash, flashEnd));
    }

    private static boolean validateSections(ByteBuffer input, byte[] elf, long tableOffset, int count, int namesIndex) {
        int namesHeader = checkedInt(tableOffset + (long) namesIndex * SECTION_HEADER_BYTES);
        long namesOffset = u32(input, namesHeader + 16);
        long namesLength = u32(input, namesHeader + 20);
        requireRange(namesOffset, namesLength, elf.length, "section name table");
        require(namesLength <= 16 * 1024, "Section name table is too large");
        boolean abiNoteFound = false;
        for (int i = 0; i < count; i++) {
            int base = checkedInt(tableOffset + (long) i * SECTION_HEADER_BYTES);
            long nameOffset = u32(input, base);
            long type = u32(input, base + 4);
            long flags = u32(input, base + 8);
            long address = u32(input, base + 12);
            long fileOffset = u32(input, base + 16);
            long size = u32(input, base + 20);
            require(type != SHT_REL && type != SHT_RELA, "Pending ELF relocation is forbidden");
            String name = readName(elf, namesOffset, namesLength, nameOffset);
            boolean allocated = (flags & 2) != 0;
            if (allocated) {
                require(name.equals(".text") || name.equals(".data") || name.equals(".bss"),
                        "Unknown allocated ELF section: " + name);
                if (name.equals(".text")) require(address + size <= ToolchainProfile.MAX_FLASH_BYTES,
                        "Text section exceeds flash");
                else require(address >= SRAM_START && address + size <= SRAM_END, "Data section exceeds SRAM");
            } else {
                require(METADATA_SECTIONS.contains(name), "Unknown ELF section: " + name);
            }
            if (type != 8) requireRange(fileOffset, size, elf.length, "section " + name);
            if (name.equals(".note.craftonica.abi")) {
                require(type == 7 && size == 28, "Craftonica ABI note has invalid shape");
                byte[] expected = new byte[] {
                        11, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 0,
                        'C', 'R', 'A', 'F', 'T', 'O', 'N', 'I', 'C', 'A', 0, 0,
                        1, 0, 1, 0 };
                require(Arrays.equals(expected, Arrays.copyOfRange(elf, checkedInt(fileOffset),
                        checkedInt(fileOffset + size))), "Craftonica ABI note contents are invalid");
                abiNoteFound = true;
            }
        }
        return abiNoteFound;
    }

    private static void validateAllocatedSectionCoverage(ByteBuffer input, long tableOffset, int count,
            long[] virtualStarts, long[] virtualEnds) {
        for (int i = 0; i < count; i++) {
            int base = checkedInt(tableOffset + (long) i * SECTION_HEADER_BYTES);
            long flags = u32(input, base + 8);
            if ((flags & 2) == 0) continue;
            long start = u32(input, base + 12);
            long size = u32(input, base + 20);
            require(size <= Long.MAX_VALUE - start, "Allocated section range overflows");
            long end = start + size;
            boolean covered = false;
            for (int j = 0; j < virtualStarts.length; j++) {
                if (start >= virtualStarts[j] && end <= virtualEnds[j]) {
                    covered = true;
                    break;
                }
            }
            require(covered, "Allocated section is not covered by a load segment");
        }
    }

    private static String readName(byte[] elf, long tableOffset, long tableLength, long relativeOffset) {
        require(relativeOffset < tableLength, "Section name offset is invalid");
        int start = checkedInt(tableOffset + relativeOffset);
        int limit = checkedInt(tableOffset + tableLength);
        int end = start;
        while (end < limit && elf[end] != 0) end++;
        require(end < limit && end - start <= 128, "Section name is unterminated or too long");
        return new String(elf, start, end - start, StandardCharsets.US_ASCII);
    }

    private static int u8(ByteBuffer value, int offset) { return value.get(offset) & 0xff; }
    private static int u16(ByteBuffer value, int offset) { return value.getShort(offset) & 0xffff; }
    private static long u32(ByteBuffer value, int offset) { return value.getInt(offset) & 0xffffffffL; }

    private static void requireRange(long offset, long length, int maximum, String label) {
        require(offset >= 0 && length >= 0 && offset <= maximum && length <= maximum - offset,
                "Invalid " + label + " range");
    }

    private static int checkedInt(long value) {
        require(value >= 0 && value <= Integer.MAX_VALUE, "ELF offset overflows Java limits");
        return (int) value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw rejected(message);
    }

    private static IllegalArgumentException rejected(String message) {
        return new IllegalArgumentException("VERIFY_REJECTED: " + message);
    }
}
