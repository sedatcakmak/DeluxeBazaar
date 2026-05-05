# DeluxeBazaar

A fully configurable bazaar/shop plugin for Spigot, Paper and Folia
servers, inspired by the bazaar found on popular skyblock servers.
Players sell items into orders, buy from existing offers, and view
their progress across categories and items.

## Features

- Buy / sell flow with order books per item
- Category and item GUIs, fully configurable
- Player limits, taxes and per-item caps
- Folia support
- Vault economy support, with optional alternative economies
  (CoinsEngine, RoyaleEconomy, PlayerPoints, TokenManager, EcoBits, etc.)
- PlaceholderAPI support
- HeadDatabase, MMOItems, ExecutableItems, EcoItems, libreforge / eco
  item support
- Optional Redis sync via DeluxeBazaarRedis

## Requirements

- Spigot / Paper / Folia (built against API 1.13)
- Java 8 or newer
- Optional integrations (soft dependencies): Vault, CoinsEngine,
  HeadDatabase, ProtocolLib, RoyaleEconomy, PlaceholderAPI, PlayerPoints,
  TokenManager, Lands, EcoBits, ExecutableItems, MMOItems, EcoItems,
  libreforge, eco, DeluxeBazaarRedis, EdPrison, CMI

## Commands

- `/bazaar` (aliases: `/deluxebazaar`, `/bz`, `/bzr`) — open the bazaar
- `/bazaaradmin` (aliases: `/deluxebazaaradmin`, `/bzadmin`, `/bzradmin`) — admin tools

## Installation

1. Drop `DeluxeBazaar.jar` into your server's `plugins/` folder.
2. Start the server once to generate the default configuration.
3. Edit the files in `plugins/DeluxeBazaar/`, then reload or restart.

## Configuration

The plugin generates a set of YAML files under `plugins/DeluxeBazaar/`
covering category and item definitions, GUI layouts, messages, economy
settings and integration toggles. Most behavior — item lists, prices,
limits, GUI titles, sounds — is driven from these files.

## License

See `LICENSE`.
