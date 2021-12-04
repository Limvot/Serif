let
    sources = import ./nix/sources.nix;
    pkgs = import sources.nixpkgs { };
in
pkgs.mkShell {
    buildInputs = with pkgs; [
        cmake
        emscripten
        wabt binaryen
        jre8
    ];
}

