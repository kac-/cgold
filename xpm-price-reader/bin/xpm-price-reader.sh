#!/bin/sh
DIR=$(dirname $(readlink -f $0))
cd $DIR/..
../bin/pomrunner.sh com.kactech.cgold.tools.XpmPriceReader
