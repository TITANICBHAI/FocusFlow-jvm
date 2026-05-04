{pkgs}: {
  deps = [
    pkgs.freetype
    pkgs.fontconfig
    pkgs.libGL
    pkgs.xorg.libXtst
    pkgs.xorg.libXxf86vm
    pkgs.xorg.libXi
    pkgs.xorg.libXrender
    pkgs.xorg.libXext
    pkgs.xorg.libX11
    pkgs.gradle
  ];
}
