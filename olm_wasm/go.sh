#! /usr/bin/env nix-shell
#! nix-shell -i bash
rm -rf olm* WasmOlm.jar xyz
git clone https://gitlab.matrix.org/matrix-org/olm.git
pushd olm
sed "s/-O3/-O1/" -i Makefile
sed "s/CFLAGS += /CFLAGS += -g2 /" -i Makefile
sed "s/CXXFLAGS += /CXXFLAGS += -g2 /" -i Makefile
sed "s/EMCCFLAGS = --closure 1/EMCCFLAGS = -g2 /" -i Makefile
#sed "s/EMCCFLAGS = --closure 1/EMCCFLAGS = /" -i Makefile
 
make js || true # fails while compiling some JS, we don't care
cp javascript/olm.wasm ../
popd
wasm2wat olm.wasm > olm.wat
#wget https://github.com/cretz/asmble/releases/download/0.4.0/asmble-0.4.0.tar
#tar xf asmble-0.4.0.tar
./asmble/bin/asmble compile olm.wasm xyz.room409.WasmOlm -out olm.class
mkdir -p xyz/room409
cp olm.class xyz/room409/WasmOlm.class
jar cf WasmOlm.jar xyz
cp WasmOlm.jar ../serif_shared/libs
