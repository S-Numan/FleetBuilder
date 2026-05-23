#!/bin/sh

# Read mod name from file
MOD_FOLDER_NAME=$(head -n 1 ./mod-folder-name.txt)

echo "Folder name will be $MOD_FOLDER_NAME"
###


chmod +x ./zipMod.sh
sh ./zipMod.sh "./../.." "$MOD_FOLDER_NAME"
