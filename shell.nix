{nixpkgs ? (import /vagrant/nixpkgs {}) }:
with nixpkgs; callPackage ./default.nix {}