let
    sources = import ./nix/sources.nix;
    pkgs = import sources.nixpkgs { config = { allowUnfree = true; }; };
in
pkgs.mkShell {
    buildInputs = with pkgs; [
        #android-studio
        androidStudioPackages.canary
        jdk
        steam-run-native
    ];
    _JAVA_AWT_WM_NONREPARENTING=1;
}

