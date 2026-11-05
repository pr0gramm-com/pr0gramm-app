{
  description = "pr0gramm android app";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f (import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      }));
    in
    {
      devShells = forAllSystems (
        pkgs:
        let
          # versions match compileSdk and the AGP 9.5 default build-tools
          android = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ "35" ];
            buildToolsVersions = [ "36.0.0" ];
          };
          sdk = "${android.androidsdk}/libexec/android-sdk";
        in
        {
          default = pkgs.mkShell {
            # CI uses temurin 17
            packages = [
              pkgs.jdk17
              pkgs.android-tools # native adb, the SDK one is x86_64 only
            ];

            ANDROID_HOME = sdk;
            ANDROID_SDK_ROOT = sdk;
            JAVA_HOME = pkgs.jdk17.home;
          };
        }
      );
    };
}
