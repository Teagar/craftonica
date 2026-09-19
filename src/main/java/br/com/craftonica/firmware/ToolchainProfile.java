package br.com.craftonica.firmware;

public final class ToolchainProfile {
    public static final String ID = "craftonica-avr-uno:1/core-1.8.6/gcc-7.3.0-atmel3.6.1-arduino7";
    public static final String MCU = "atmega328p";
    public static final long CPU_HZ = 16_000_000L;
    public static final int INSTRUCTION_ABI = 1;
    public static final int MMIO_ABI = 1;
    public static final int MAX_FLASH_BYTES = 32_256;
    public static final int MAX_SRAM_BYTES = 2_048;
    public static final int MAX_EEPROM_BYTES = 1_024;
    public static final String PROFILE_HASH_HEX = "d181b87d807dcddc429c130699245cfb38c410fc7c1898323e4d263fbbe9ef36";
    public static final String TOOLCHAIN_TREE_HASH_HEX = "dcf1200210b85a7270ead0497f9c79a3bc973636f801e68d7de40289ca4ddd81";
    public static final String CORE_TREE_HASH_HEX = "6cf1485643317311afbc5abf4714d11f0e25a0899c4bd1da94943e1b2eab31ba";

    private static final byte[] PROFILE_HASH = FirmwareHashes.fromHex(PROFILE_HASH_HEX);

    private ToolchainProfile() {
    }

    public static byte[] profileHash() { return PROFILE_HASH.clone(); }
}
