## [1.0.6](https://github.com/StefanElijah/zynema-project/compare/v1.0.5...v1.0.6) (2026-10-01)

### Bug Fixes

- **user-service:** read typed uuid fields in the role dead letters ([6c4d1dc](https://github.com/StefanElijah/zynema-project/commit/6c4d1dc1add91f8fe7c6bb584dc302b980ee27f6))
- **user-service:** wire the admin client through its annotated constructor ([89f6609](https://github.com/StefanElijah/zynema-project/commit/89f6609f8af2212336a324fb1448ad8ee277f62c))

## [1.0.5](https://github.com/StefanElijah/zynema-project/compare/v1.0.4...v1.0.5) (2026-10-01)

### Bug Fixes

- **frontend:** close the session with the last position and show the no-hls error ([f46d432](https://github.com/StefanElijah/zynema-project/commit/f46d4326330a0557fd9a58a2039dba8b92117e75))

## [1.0.4](https://github.com/StefanElijah/zynema-project/compare/v1.0.3...v1.0.4) (2026-10-01)

### Bug Fixes

- **ci:** count the test helpers as tests in sonar ([f74c07c](https://github.com/StefanElijah/zynema-project/commit/f74c07c3c915b35bfcd55bb21e26a6aa68d05e7c))

## [1.0.3](https://github.com/StefanElijah/zynema-project/compare/v1.0.2...v1.0.3) (2026-10-01)

### Bug Fixes

- **frontend:** native buttons for the card and the modal backdrop ([9025fec](https://github.com/StefanElijah/zynema-project/commit/9025fec986f5868f30958049e1af45de000cc3e4))

## [1.0.2](https://github.com/StefanElijah/zynema-project/compare/v1.0.1...v1.0.2) (2026-10-01)

### Bug Fixes

- **ci:** activate jacoco so there is coverage to import ([2dea09f](https://github.com/StefanElijah/zynema-project/commit/2dea09f386fc29afb33b2a08490559b904ef0e7c))
- **ci:** classify the test files as tests in sonar ([a4d7f0a](https://github.com/StefanElijah/zynema-project/commit/a4d7f0a93f469772f2d2c8cf76da63d24848e40f))
- **frontend:** keep the card and oidc config off the sonar list ([38b2645](https://github.com/StefanElijah/zynema-project/commit/38b2645331c0e2fb869faeafaf515166b556f587))
- **playback:** trim the public base without a regex ([f77540e](https://github.com/StefanElijah/zynema-project/commit/f77540ef3864165c341ec2163f8ff5b0590e56c5))

## [1.0.1](https://github.com/StefanElijah/zynema-project/compare/v1.0.0...v1.0.1) (2026-10-01)

### Bug Fixes

- **ci:** point sonarcloud at the organization key ([c7c5dd9](https://github.com/StefanElijah/zynema-project/commit/c7c5dd9bff9d431a8272a8e9767bae97d3c3e663))
- **ci:** use the hyphenated organization key ([3021b5c](https://github.com/StefanElijah/zynema-project/commit/3021b5cae34beb02bdfec7028cad1e5d0aafbd1e))

# 1.0.0 (2026-09-30)

### Bug Fixes

- **bff:** document the checkout idempotency key in the spec ([cd8ded1](https://github.com/StefanElijah/zynema-project/commit/cd8ded1a7d9e691bf12cf749cab43a3906aa0cad))
- **compose:** replace the corrupted section banners ([373a9d4](https://github.com/StefanElijah/zynema-project/commit/373a9d43d3c0230714a476fe1f88e60737ceaa44))
- **fase-7:** discard the losing start instead of committing an orphan event ([826f642](https://github.com/StefanElijah/zynema-project/commit/826f642d208550093f8297896fb824019eb7b57b))
- **frontend:** build the image with the workspace contracts package ([15686a1](https://github.com/StefanElijah/zynema-project/commit/15686a1b62bf9e1c000953facde2a902faa2a4b3))
- **observability:** make the metrics and tracing wiring apply ([8ec8aad](https://github.com/StefanElijah/zynema-project/commit/8ec8aad4c7e3a5ab9cb89605145caa660c31fea7))
- **user-service:** keep springdoc's swagger annotations on the classpath ([8b55ffc](https://github.com/StefanElijah/zynema-project/commit/8b55ffc0051ccdff77b4cc2082084d108d1aa460))

### Features

- **fase-0:** monorepo skeleton, services skeleton, infra, docs, CI ([cee6b99](https://github.com/StefanElijah/zynema-project/commit/cee6b99f773d57c3156416a98a99c1908121115b))
- **fase-10:** publish the openapi documents and release from main ([5d719e9](https://github.com/StefanElijah/zynema-project/commit/5d719e967865926546af8ff7365664cda5f3cd1d))
- **fase-1:** service discovery, config server and gateway fully working ([615ce36](https://github.com/StefanElijah/zynema-project/commit/615ce362cfe81194b8f0226c4886dfeb3eccb9d8))
- **fase-2:** catalog and user domains with persistence, cache and tests ([8c03045](https://github.com/StefanElijah/zynema-project/commit/8c03045ff07139fc8bf37962c15a6313a5dbc370))
- **fase-3:** authentication with Keycloak end to end ([1e60715](https://github.com/StefanElijah/zynema-project/commit/1e60715257122af787cc67a6dce8117a247560c1))
- **fase-4:** domain services, resilience and edge rate limiting ([3d0ae20](https://github.com/StefanElijah/zynema-project/commit/3d0ae2068d1b34af88815ef847393ff2adb912e2))
- **fase-5:** catalog cqrs read model projected in the write transaction ([5075fcc](https://github.com/StefanElijah/zynema-project/commit/5075fcc0b319ba58cd38bb131c1f7d69dd2a60f5))
- **fase-5:** reactive bff composing screens and the playback paywall ([f349ddc](https://github.com/StefanElijah/zynema-project/commit/f349ddc6d9648b1b119e70c46aa8ff0808903fb1))
- **fase-6:** ffmpeg hls pipeline, object storage and the worker that runs it ([0f35636](https://github.com/StefanElijah/zynema-project/commit/0f3563691c01d6e778ae8fab804ba3e1873d77fd))
- **fase-6:** signed hls delivery and the watch page ([0d62e02](https://github.com/StefanElijah/zynema-project/commit/0d62e024f2cc98c232c4bc7047b17974cb1a5ffa))
- **fase-7:** event-sourced playback sessions ([cdbf342](https://github.com/StefanElijah/zynema-project/commit/cdbf342b15dcc6dda6e7397f20e610442ef0e254))
- **fase-7:** kafka foundations: topics, json schema contracts and dlt ([5a66b12](https://github.com/StefanElijah/zynema-project/commit/5a66b12a1d3681fb25d76a7b804f5b316f7ef9fb))
- **fase-7:** notification consumers and the choreographed saga ([6b004e6](https://github.com/StefanElijah/zynema-project/commit/6b004e6c3a3c82c329b32b5145b979d78dd72a0b))
- **fase-7:** orchestrated saga and the coordination comparison ([32dc950](https://github.com/StefanElijah/zynema-project/commit/32dc950c9672d39d58cde0000e43707c33dc072f))
- **fase-7:** transactional outbox in payment-service ([8c9cd6a](https://github.com/StefanElijah/zynema-project/commit/8c9cd6a7956f864584d52cf954da52aed572f0fe))
- **fase-8:** bff surface for the spa ([d1e1df7](https://github.com/StefanElijah/zynema-project/commit/d1e1df77cb4b8af227d3cdfd7856ab589a7a548e))
- **fase-8:** typed api client, query layer and app shell ([efecf91](https://github.com/StefanElijah/zynema-project/commit/efecf918a68fe3fc1babedb874fa0c8f550dadcb))
- **fase-8:** typed pages, custom player controls and e2e ([fd073f4](https://github.com/StefanElijah/zynema-project/commit/fd073f4f76e1da8cdb1a050caf51ab2ba1a6d2f0))
- **fase-8:** validate the profile form with zod and react hook form ([321ddb1](https://github.com/StefanElijah/zynema-project/commit/321ddb184a50d88876f1e8ce63c43bbb13218dc5))
- **fase-9:** file_sd discovery from eureka and alert rules ([0d2528b](https://github.com/StefanElijah/zynema-project/commit/0d2528bf53af85a0a8c31367ea3002c932eea687))
- **fase-9:** provision grafana and correlate logs, traces and metrics ([97747d4](https://github.com/StefanElijah/zynema-project/commit/97747d4a87c14c9b962f18e4f37e60f7ef0a73db))
