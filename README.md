LUKAS' CHUNK OBSERVER
=====================

A lightweight, server-side Fabric mod that logs how long each player spends in every chunk to an
SQLite database - the raw data for activity heatmaps.
Part of [LUKAS' Mod Collection](https://modrinth.com/user/TEvJ4LS3).


## Features

- Tracks per-chunk playtime for every player, per dimension.
- Stores each activity as one row in an SQLite database (`chunk_activity` table).


## Configuration

Created on first run at `config/lukaschunkobserver.toml`:

| Key | Default | Description |
| --- | --- | --- |
| `databasePath` | `""` | SQLite file location. Empty uses the per-world default `<world>/lukaschunkobserver.sqlite`. |
| `minDurationMs` | `1000` | Chunk sessions shorter than this (in milliseconds) are discarded. |


## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api)


## Reporting Issues

Please use the [issue tracker](https://github.com/lukasschr/LukasChunkObserver/issues) to report bugs, crashes, and other issues.
