# Credits and licenses

## Libraries (runtime)

| Component | Version | License |
|---|---|---|
| [libGDX](https://libgdx.com) core, LWJGL3 backend, gdx-bullet, gdx-freetype (+ platform natives) | 1.14.2 | Apache-2.0 |
| Bullet Physics (bundled in gdx-bullet natives) | via libGDX | zlib |
| FreeType (bundled in gdx-freetype natives) | via libGDX | FreeType License (BSD-style) |
| [LWJGL 3](https://www.lwjgl.org) (GLFW, OpenGL, OpenAL, stb, jemalloc) | 3.4.3 | BSD-3-Clause (bundled natives under their own permissive licenses) |
| gdx-jnigen-loader | 2.5.2 | Apache-2.0 |

## Build and test tooling

| Component | Version | License |
|---|---|---|
| Gradle (wrapper) | 9.8.0 | Apache-2.0 |
| [gdx-liftoff](https://github.com/libgdx/gdx-liftoff) project layout and LWJGL3 build template | 1.14.2.3 | Apache-2.0 |
| [Construo](https://github.com/fourlastor-alexandria/construo) packaging plugin | 2.2.3 | MIT |
| foojay toolchain resolver | 1.0.0 | Apache-2.0 |
| JUnit Jupiter | 5.11.4 | EPL-2.0 (test only) |
| Eclipse Temurin JDK 21 (bundled by packaged builds) | 21.0.10 | GPLv2 with Classpath Exception |

`lwjgl3/src/main/java/com/alteredarena/lwjgl3/StartupHelper.java` is adapted from gdx-liftoff
(Copyright 2020 damios, Apache-2.0). Its license header is kept, and it now forwards program arguments and native
access to the relaunched JVM.

## Assets

| Asset | Source | License |
|---|---|---|
| Chakra Petch Bold / SemiBold (`assets/fonts/`) | [Google Fonts](https://github.com/google/fonts/tree/main/ofl/chakrapetch), Copyright 2018 The Chakra Petch Project Authors | SIL Open Font License 1.1 (`assets/fonts/ChakraPetch-OFL.txt`) |
| All sound effects (`assets/sounds/*.wav`) | Synthesized by `tools/.../SoundGenerator.java` | Project's own |
| Window/app icons | Drawn by `tools/.../IconGenerator.java` | Project's own |
| 3D models, textures, UI art and power-up icons | Generated at runtime in code (`render/ModelFactory`, `render/Textures`, `ui/Icons`, `ui/UiAssets`) | Project's own |

The project uses no proprietary or paid assets.
