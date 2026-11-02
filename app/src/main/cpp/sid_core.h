// SPDX-License-Identifier: GPL-2.0-or-later
// Thin wrapper over libsidplayfp: load a tune from memory, pick a subsong, render PCM.
// Kept free of JNI so the host harness (tools/sidrender.cpp) exercises the same code.
#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

struct SidSession;

struct SidTuneMeta {
    unsigned songs = 0;
    unsigned startSong = 0;
    std::vector<std::string> info;   // title, author, released (PSID header strings)
    std::string format;
    bool needsRoms = false;          // RSID / BASIC tunes need the real C64 ROMs
};

// Parses the tune header only. Returns false and fills err when the bytes aren't a tune.
bool sid_probe(const uint8_t* data, size_t len, SidTuneMeta& meta, std::string& err);

// subsong is 1-based; 0 = the tune's start song. Returns nullptr and fills err on failure.
SidSession* sid_open(const uint8_t* data, size_t len, unsigned subsong, unsigned rate, std::string& err);

unsigned sid_channels(const SidSession* s);

// Renders up to maxFrames frames of interleaved 16-bit PCM into out (sized maxFrames * channels).
// Returns frames written, or -1 on emulation error.
int sid_render(SidSession* s, int16_t* out, unsigned maxFrames);

void sid_close(SidSession* s);
