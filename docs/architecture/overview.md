# Architecture Overview (reference)

Condensed, reusable reference for audits. Source of truth for module facts is `CLAUDE.md`;
this file adds the dependency graph and cross-cutting mechanisms in one place.

## Module dependency graph

```
app ─────► common_ui, core, data, domain
data ────► core, domain
core ────► domain
domain ──► (intended: none — currently pulls androidx.core.ktx, material, dagger.hilt)
common_ui► (Compose theme/icons, Coil, QR)
```

- **domain** — models, `I*` repository interfaces (34), use cases (21).
- **core** — crypto/blockchain primitives, `BlockchainRegistry`/`AssetRegistry`, `KeyManager`,
  encryption. Data-driven from `core/src/main/assets/networks.json` + `assets.json`.
- **data** — repository impls (16), data sources, DI (`DataModule`, `NetworkModule`,
  `SocketModule`), relayer `BuildConfig`.
- **app** — 42 Compose screen files, 13 ViewModels, session/security, DI entry point.

## Cross-cutting mechanisms

- **DI:** Hilt. Core modules: `core/di/{ApplicationScope,CryptoModule,ManagerModule}`,
  `data/di/{DataModule,NetworkModule,SocketModule,NetworkConnectionInterceptor}`.
- **Chains:** Registry + `NetworkFactory` strategy per `NetworkType` (EVM/UTXO/BITCOIN/TVM).
- **Transport:** `IChainDataSource` with DIRECT (per-chain RPC) vs PROXY (`ProxyChainDataSource`)
  selected by `IBlockchainConnectionModeProvider`.
- **Transfers:** `UnifiedTransferCoordinator` single entry point; gasless via EVM/Tron coordinators.
- **Navigation:** state-based in `MainScreen` (no Navigation-Compose; `nav_graph.xml` removed).
- **ViewModels:** extend `BaseViewModel(ErrorManager)`.

## ADR backlog (to be written)
- Why state-based navigation over Navigation-Compose.
- DIRECT/PROXY transport toggle rationale.
- On-device signing & key lifecycle.
- BouncyCastle `bcprov-jdk18on:1.73` pin.
