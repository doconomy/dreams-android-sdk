# Changelog

All notable changes to this library will be documented here.

## 1.0.0 — unreleased

Android 15 / 16 compatibility. `compileSdk` and `targetSdk` raised to 36.

### Breaking

* `DreamsView` now applies `systemBars` insets as its own padding. Remove any
  `OnApplyWindowInsetsListener` you attach to the Dreams view, or padding will
  be applied twice.
* `DreamsView` now registers its own `OnBackPressedCallback` while attached,
  enabled only when the WebView has history. Remove your own back callback for
  the Dreams view. This is what allows Predictive Back to show the correct
  preview animation. `canGoBack()` and `goBack()` remain public for manual
  control.

### Features

* Keyboard insets are handled on API 35+, where `adjustResize` is ignored
  because edge-to-edge is mandatory. `DreamsView` drives WebView padding from
  the IME animation instead. Keep `adjustResize` in your manifest — it is still
  required on API 21–34.

### Bug Fixes

* Replaced `setAppCachePath` / `setAppCacheEnabled` with reflective calls. Both
  were removed in API 33 and no longer compile, but are still applied on API
  21–29 where they exist.

### Internal

* Added an explicit `androidx.activity` dependency; it was previously only
  present transitively via appcompat.
* Bumped Robolectric to 4.16.1. The previous 4.13 caps at SDK 34 and failed
  every unit test against `targetSdk 36`.
* Added packaging excludes for duplicate `META-INF` license files pulled in by
  transitive test dependencies.
* Repaired the `connectedAndroidTest` suite, which had not compiled since
  August 2024 — `testNamespace` was changed without updating the imports that
  referenced `TestActivity`.

## [0.6.0](https://github.com/doconomy/dreams-android-sdk/compare/0.5.1...0.6.0) (2021-09-16)


### Features

* add navigate to function to dreams view ([beac472](https://github.com/doconomy/dreams-android-sdk/commit/beac47293fe43074d99753e8367e4fb65d565147))
* allow launch to specify what location to open ([5f0318f](https://github.com/doconomy/dreams-android-sdk/commit/5f0318f06b23087a660c0fb7c84ac2e1caa8d40d))

### [0.5.1](https://github.com/doconomy/dreams-android-sdk/compare/0.5.0...0.5.1) (2021-05-05)

### Bug Fixes

* share dialog intent type and improved unit tests

### [0.5.0](https://github.com/doconomy/dreams-android-sdk/compare/0.4.2...0.5.0) (2021-04-28)

* Share functionality, that can be triggered using following JS code
* `var payload = {"text":"Text to share", "url": "url"}; if (window.JSBridge) { window.JSBridge.share(payload); }`

### [0.4.2](https://github.com/doconomy/dreams-android-sdk/compare/0.4.1...0.4.2) (2021-03-16)


### Bug Fixes

* always have the dreams web view intercept requests ([6b70d98](https://github.com/doconomy/dreams-android-sdk/commit/6b70d98fdf165166d94a2e8cc9bb01bad729c24a))

### [0.4.1](https://github.com/doconomy/dreams-android-sdk/compare/0.4.0...0.4.1) (2021-02-25)


### Features

* default dreams view to focusable in touch mode ([1e186d4](https://github.com/doconomy/dreams-android-sdk/commit/1e186d42e3cd95ed84a962b1c73916e20ed1ac07))

## [0.4.0](https://github.com/doconomy/dreams-android-sdk/compare/0.3.0...0.4.0) (2021-02-03)


### ⚠ BREAKING CHANGES

* DreamsView.launch() now requires a locale to be sent.

### Refactors

* require a locale when launching dreams ([b215dea](https://github.com/doconomy/dreams-android-sdk/commit/b215deaae14e34967b7e7284c14bf2fb2c3c434d))

## [0.3.0](https://github.com/doconomy/dreams-android-sdk/compare/0.2.2...0.3.0) (2021-01-27)


### ⚠ BREAKING CHANGES

* `DreamsView.launch` no longer takes a location.

### Features

* allow callers of launch to react to errors ([d42c81f](https://github.com/doconomy/dreams-android-sdk/commit/d42c81f7ebefcbe725b48e0a41bd4afcb44b7c62))


### Refactors

* removed unused parameter from launch ([88c65eb](https://github.com/doconomy/dreams-android-sdk/commit/88c65eb2bc45f7badb06cf16a9976c3ab0b510a4))

## [0.2.2](https://github.com/doconomy/dreams-android-sdk/compare/0.2.1...0.2.2) (2021-01-15)

### ⚠ Breaking Changes

* change how the singleton instance is configured. Instead of using multiple params, change to use a single object containing all the data needed for configuring the dreams instance.
* rename `RequestInterface.open` to `RequestInterface.launch`. Instead of taking a token directly, you now need to wrap it in a `Credentials` object.
* replace `IdTokenExpired` with `CredentialsExpired` event.

## [0.2.1](https://github.com/doconomy/dreams-android-sdk/compare/0.2.0...0.2.1) (2021-01-13)

### Bug Fixes

* update init post url
* change names in init payload
* set cookie from init call
