# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What this is

MegaWallet is a **non-custodial multi-chain crypto wallet** for Android. It supports EVM chains
(Ethereum, BSC, etc.), Bitcoin/UTXO chains (Bitcoin, Dogecoin — via bitcoinj + bitcoin-kmp), and
Tron (TVM). Private keys are derived and used on-device; only signed payloads leave the device.
UI is 100% Jetpack Compose. Many code comments are in Persian/Farsi — this is expected.

## Build & test commands

Use the Gradle wrapper (`./gradlew`, or `gradlew.bat` on Windows). Modules: `:app`, `:common_ui`,
`:core`, `:data`, `:domain`.

```bash
./gradlew assembleDebug              # build debug APK
./gradlew :data:testDebugUnitTest    # run one module's JVM unit tests (most tests live in :data)
./gradlew test                       # all JVM unit tests
./gradlew connectedAndroidTest       # instrumented tests (needs a device/emulator)
./gradlew lint                       # Android lint

# Run a single test class or method:
./gradlew :data:testDebugUnitTest --tests "com.mtd.data.repository.transfer.UnifiedTransferCoordinatorTest"
./gradlew :data:testDebugUnitTest --tests "*.GaslessRouteResolverTest.someMethod"
```

Unit tests use JUnit4 + MockK + OkHttp MockWebServer. Instrumented tests use Hilt testing. The
bulk of meaningful test coverage is in `:data`; other modules mostly have placeholder `ExampleUnitTest`.

## Module architecture (clean architecture, strict dependency direction)

```
app ─────► common_ui, core, data, domain
data ────► core, domain
core ────► domain
domain ──► (no project deps — pure Kotlin models, interfaces, use cases)
```

- **`domain`** — framework-free contracts and models. All repository interfaces live in
  `domain/.../interfaceRepository/` prefixed `I*` (e.g. `IWalletRepository`, `IChainDataSource`
  consumers, `IUnifiedTransferCoordinator`). Use cases in `domain/.../usecase/`. Never add Android
  or third-party SDK deps here.
- **`core`** — crypto and blockchain primitives: `BlockchainNetwork` abstraction, key derivation,
  `KeyManager`, encryption, and the **registries** (see below). Depends on web3j, bitcoinj,
  bitcoin-kmp, BouncyCastle.
- **`data`** — repository implementations, data sources, DTOs/mappers, networking (Retrofit/OkHttp),
  Hilt modules. Owns the relayer/proxy `BuildConfig` fields.
- **`common_ui`** — shared Compose theme (`MegaWalletTheme`), icons, and reusable UI deps (Coil, QR).
- **`app`** — Compose screens, ViewModels, DI entry point, activities, security/session coordination.

## Key architectural patterns

**Registry + strategy for chains.** `core/.../registry/BlockchainRegistry` (implements
`INetworkCatalog`) builds `BlockchainNetwork` instances via `NetworkFactory` strategies
(`EvmNetworkFactory`, `BitcoinNetworkFactory`, `UtxoNetworkFactory`, `TronNetworkFactory`) keyed by
`NetworkType`. `AssetRegistry` does the same for tokens. **Networks and assets are data-driven**:
they load from `core/src/main/assets/networks.json` and `assets.json`. To add a chain or token,
edit the JSON — don't hardcode a `when(networkType)` branch; extend the factory set if a genuinely
new `NetworkType` is needed.

**DIRECT vs PROXY transport.** Every chain read/broadcast goes through `IChainDataSource`.
`ChainDataSourceFactory` picks either a direct RPC source (`EvmDataSource`, `BitcoinDataSource`,
`TronDataSource`) or `ProxyChainDataSource` (routes through the centralized Mobile Blockchain Proxy
at `/api/mobile/v1`) based on `IBlockchainConnectionModeProvider.currentMode()`. Both return the
**same domain types**, so the toggle is transparent to ViewModels. The mode is a persisted user
preference (`DefaultBlockchainConnectionModeProvider`, read synchronously via an in-memory cache).
When changing send/read logic, keep DIRECT and PROXY paths behaviorally equivalent.

**Unified transfers + gasless.** `UnifiedTransferCoordinator` (implements
`IUnifiedTransferCoordinator`) is the single entry point for sends. It branches on `NetworkType`
and connection mode, and delegates gasless (sponsored) transfers to `EvmGaslessCoordinator` /
`TronGaslessCoordinator` under `data/.../repository/gasless/`. Signing always happens locally;
only signed payloads are relayed.

**Web3 session auth.** `WalletSessionAuthCoordinator` (in `app/.../session/`) starts on
`MainActivityCompose.onCreate`, mints a JWT via wallet signature after unlock, and connects the
realtime WebSocket (`RealtimeConnectionGateway`). Auth message signing is in
`data/.../repository/auth/`.

**Navigation is state-based, not NavController.** `MainScreen` composes screens by observing
ViewModel state (tabs = `MainTab`, selected asset id, etc.); there is no Navigation-Compose graph or
SafeArgs. When adding a screen, wire it through state in `MainScreen` / the relevant ViewModel, not
a `NavHost`. ViewModels extend `BaseViewModel` (takes `ErrorManager`) and live under
`app/.../viewmodel/`.

**Entry flow.** Launcher is `WelcomeActivityCompose` (onboarding: create/import wallet) →
`MainActivityCompose` (main app, `FragmentActivity` for biometric prompt support). App class is
`MegaWalletApplication` (`@HiltAndroidApp`, warms up dynamic config, configures Coil image loader).

## Conventions & gotchas

- **DI:** Hilt everywhere. Bind new implementations in the relevant module's Hilt module
  (`data/.../di/DataModule.kt`, `NetworkModule.kt`, `SocketModule.kt`; `core/.../di/`). Repository
  interfaces are always in `domain`, implementations in `data`/`core`.
- **Relayer endpoints are `BuildConfig` fields in `:data`**, not hardcoded in Kotlin:
  `RELAYER_BASE_URL`, `RELAYER_HOST`, `RELAYER_WS_URL`. They currently point at a plaintext
  `http://`/`ws://` dev IP with `TODO(release)` markers to switch to TLS + cert pinning. Don't
  reintroduce hardcoded IPs elsewhere.
- **BouncyCastle is force-pinned to `bcprov-jdk18on:1.73`** in the root `build.gradle.kts`
  `resolutionStrategy` to resolve a duplicate-class conflict between the jdk15to18 and jdk18on
  variants pulled in by the web3/bitcoin libs. Needed because Conscrypt can't resolve secp256k1 by
  curve name. Don't remove or downgrade this.
- **`minSdk 26`, `targetSdk`/`compileSdk 36`, JVM target 17.** minSdk 26 gives native multidex, so
  the `androidx.multidex` dependency was intentionally removed.
- Dependencies are managed via the version catalog at `gradle/libs.versions.toml` (with `bundles`).
  Add deps there, reference as `libs.*`.
