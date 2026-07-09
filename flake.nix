{
  description = "A very basic flake";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      pkgs = import nixpkgs {
        system = "x86_64-linux";
        config.allowUnfree = true;
        config.android_sdk.accept_license = true;
      };
    in
    {
      devShells.x86_64-linux.default = pkgs.mkShell rec {
        buildInputs = [
          pkgs.android-studio-full
        ];

        ANDROID_HOME = "./sdk";
        ANDROID_NDK_ROOT = "${ANDROID_HOME}/ndk-bundle";

        # emulator does not support wayland
        QT_QPA_PLATFORM = "xcb";
      };
    };
}
