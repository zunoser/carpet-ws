{
  description = "Fabric Carpet Bot API development environment";

  inputs.nixpkgs.url = "https://flakehub.com/f/NixOS/nixpkgs/0.1";

  outputs =
    { self, nixpkgs, ... }:
    let
      javaVersion = 21;

      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];

      forEachSupportedSystem =
        f:
        nixpkgs.lib.genAttrs supportedSystems (
          system:
          f {
            inherit system;
            pkgs = import nixpkgs {
              inherit system;
              overlays = [ self.overlays.default ];
            };
          }
        );
    in
    {
      overlays.default =
        final: prev:
        let
          jdk = prev."jdk${toString javaVersion}";
        in
        {
          inherit jdk;
          gradle = prev.gradle_8.override { java = jdk; };
        };

      devShells = forEachSupportedSystem (
        { pkgs, system }:
        {
          default = pkgs.mkShellNoCC {
            packages = with pkgs; [
              gradle
              jdk
              self.formatter.${system}
            ];

            shellHook = ''
              export JAVA_HOME="${pkgs.jdk}"
              export GRADLE_USER_HOME="$PWD/.gradle-home"
              export GRADLE_OPTS="-Xmx2G -Dorg.gradle.daemon=true -Dorg.gradle.parallel=true -Dorg.gradle.caching=true"

              mkdir -p "$GRADLE_USER_HOME"
            '';
          };
        }
      );

      formatter = forEachSupportedSystem ({ pkgs, ... }: pkgs.nixfmt);
    };
}
