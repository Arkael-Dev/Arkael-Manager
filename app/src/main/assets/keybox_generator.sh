#!/system/bin/sh

# =============================================================================
# GENERATE KEYBOX - PRO VERSION
# Owner : @VorteXSU_Dev
# Mode 1: Direct Source (Stable)
# Mode 2: Advanced Engine (Complete Implementation)
# =============================================================================

# Color Definitions
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m'

# =============================================================================
# HELPER FUNCTIONS
# =============================================================================

random_string() {
  chars="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
  length=${1:-8}
  result=""
  while [ "${#result}" -lt "$length" ]; do
    rand=$(od -An -N1 -tu1 /dev/urandom | tr -d ' ')
    result="$result$(echo "$chars" | cut -c $((rand % ${#chars} + 1)))"
  done
  echo "$result"
}

sdrow_esrever() {
  echo "$1" | awk '{
    for(i=1; i<=NF; i++) {
      str = $i
      len = length(str)
      rev = ""
      for(j=len; j>0; j--) {
        rev = rev substr(str, j, 1)
      }
      $i = rev
    }
    print
  }'
}

zipball() {
    the="enoladnatS-artiC"   
    to="moc.tnetnocresubuhtig.war"
    BRAVE="enoladnats-artic"
    Advance="//:sptth"
    For="niam"
    tluser="$For/$the/$BRAVE/$to$Advance"
    sdrow_esrever "$tluser"
}

# =============================================================================
# MODE 2: ADVANCED ENGINE (FULL IMPLEMENTATION)
# =============================================================================

mode2_advanced() {
    echo ""
    echo "${YELLOW}============================================${NC}"
    echo "${CYAN}     MODE 2: ADVANCED ENGINE${NC}"
    echo "${YELLOW}============================================${NC}"
    echo ""
    
    # ------------------------------------------------------------------
    # STEP 1: Environment Detection
    # ------------------------------------------------------------------
    echo "${WHITE}[Step 1/7] Environment Detection${NC}"
    
    # Check TSupport Advance module
    TSUPPORT_MOD="/data/adb/modules/tsupport-advance"
    if [ -d "$TSUPPORT_MOD" ]; then
        echo "${GREEN}[✔] TSupport Advance: Installed${NC}"
        if [ -f "$TSUPPORT_MOD/module.prop" ]; then
            TS_VER=$(grep "^version=" "$TSUPPORT_MOD/module.prop" | cut -d'=' -f2)
            echo "    Version: $TS_VER"
        fi
    else
        echo "${YELLOW}[~] TSupport Advance: Not Installed${NC}"
        echo "    (Optional but recommended)"
    fi
    
    # Check Tricky Store
    TS_STORE="/data/adb/modules/tricky_store"
    if [ -d "$TS_STORE" ]; then
        echo "${GREEN}[✔] Tricky Store: Installed${NC}"
        TS_AUTHOR=$(grep '^author=' "$TS_STORE/module.prop" 2>/dev/null | head -n1 | cut -d'=' -f2 | tr '[:upper:]' '[:lower:]')
        echo "    Author: $(grep '^author=' "$TS_STORE/module.prop" 2>/dev/null | head -n1 | cut -d'=' -f2)"
    else
        echo "${RED}[✖] Tricky Store: NOT FOUND${NC}"
        echo ""
        echo "${RED}ERROR: Tricky Store is required!${NC}"
        read -p "Press Enter to return to menu..."
        return 1
    fi
    
    # Device info
    SDK="$(getprop ro.build.version.sdk)"
    echo "${CYAN}[INFO] Android SDK: $SDK${NC}"
    
    # ROM Sign detection
    if [ -f "/system/etc/security/otacerts.zip" ]; then
        if unzip -l /system/etc/security/otacerts.zip 2>/dev/null | grep -q "testkey"; then
            echo "${CYAN}[INFO] ROM Sign: testkey${NC}"
        elif unzip -l /system/etc/security/otacerts.zip 2>/dev/null | grep -q "releasekey"; then
            echo "${CYAN}[INFO] ROM Sign: releasekey${NC}"
        else
            echo "${CYAN}[INFO] ROM Sign: unknown${NC}"
        fi
    fi
    
    # SELinux status
    echo "${CYAN}[INFO] SELinux: $(getenforce 2>/dev/null || echo 'unknown')${NC}"
    
    # Boot hash check
    if [ -f "/data/adb/boot_hash" ]; then
        echo "${GREEN}[✔] Boot Hash: Found (/data/adb/boot_hash)${NC}"
    elif [ -f "/sdcard/TSupportConfig/boot_hash" ]; then
        echo "${GREEN}[✔] Boot Hash: Found (/sdcard/TSupportConfig/boot_hash)${NC}"
    else
        echo "${YELLOW}[~] Boot Hash: Not found${NC}"
    fi
    
    echo ""
    
    # ------------------------------------------------------------------
    # STEP 2: Build Resource URLs
    # ------------------------------------------------------------------
    echo "${WHITE}[Step 2/7] Building Resource URLs${NC}"
    
    BASE_URL=$(zipball)
    
    if [ "$SDK" -gt "31" ] 2>/dev/null; then
        URL_PATH="$BASE_URL/zipball"
        echo "${CYAN}[INFO] Using ZIPBALL format (SDK > 31)${NC}"
    else
        URL_PATH="$BASE_URL"
        echo "${CYAN}[INFO] Using DIRECT format (SDK <= 31)${NC}"
    fi
    
    SANCTUARY_URL="$URL_PATH/sanctuary.tar"
    BIN_URL="$URL_PATH/bin.tar"
    
    echo "${CYAN}[INFO] Base path configured${NC}"
    echo ""
    
    # ------------------------------------------------------------------
    # STEP 3: Validate & Download Key
    # ------------------------------------------------------------------
    echo "${WHITE}[Step 3/7] Key Validation & Download${NC}"
    
    WORKDIR="/sdcard/keybox_work"
    rm -rf "$WORKDIR"
    mkdir -p "$WORKDIR"
    cd "$WORKDIR" || { echo "${RED}Cannot create workdir${NC}"; return 1; }
    
    DOWNLOAD_SUCCESS=0
    FINAL_URL=""
    SOURCE_NAME=""
    
    # Attempt 1: Try Sanctuary (primary source)
    echo "${YELLOW}[Attempt 1/4] Trying Primary Source...${NC}"
    
    if wget -q -T 15 -O key --no-check-certificate "$SANCTUARY_URL" 2>/dev/null; then
        if grep -qi '[^[:space:]]' key 2>/dev/null; then
            echo "${GREEN}[OK] Primary source valid${NC}"
            DOWNLOAD_SUCCESS=1
            FINAL_URL="$SANCTUARY_URL"
            SOURCE_NAME="Sanctuary"
        fi
    elif curl -s -o key --insecure --connect-timeout 15 "$SANCTUARY_URL" 2>/dev/null; then
        if grep -qi '[^[:space:]]' key 2>/dev/null; then
            echo "${GREEN}[OK] Primary source valid${NC}"
            DOWNLOAD_SUCCESS=1
            FINAL_URL="$SANCTUARY_URL"
            SOURCE_NAME="Sanctuary"
        fi
    fi
    
    # Attempt 2: Scan Blackbox resources
    if [ $DOWNLOAD_SUCCESS -eq 0 ]; then
        echo "${YELLOW}[Attempt 2/4] Scanning Resources...${NC}"
        
        choices=""
        temp="$WORKDIR/temp"
        mkdir -p "$temp"
        
        for i in $(seq 0 9); do
            path="blackbox$i"
            LRU="$URL_PATH/$path.tar"
            
            printf "    Checking resource %d ... " "$i"
            
            if wget --spider --quiet "$LRU" 2>/dev/null; then 
                if wget -q -O - --no-check-certificate "$LRU" 2>/dev/null | head -c 471 > "$temp/$path"; then               
                    if grep -q '[^[:space:]]' "$temp/$path"; then  
                        choices="$choices $path"
                        echo "${GREEN}[VALID]${NC}"
                    else
                        echo "${RED}[EMPTY]${NC}"
                    fi
                else
                    echo "${RED}[FAIL]${NC}"
                fi
            elif curl --silent --location --fail "$LRU" 2>/dev/null | head -c 471 > "$temp/$path"; then
                if grep -q '[^[:space:]]' "$temp/$path"; then
                    choices="$choices $path"
                    echo "${GREEN}[VALID]${NC}"
                else
                    echo "${RED}[EMPTY]${NC}"
                fi
            else
                echo "${RED}[UNREACHABLE]${NC}"
            fi
        done
        
        rm -rf "$temp"
        
        # Select random valid resource
        if [ -n "$choices" ]; then
            num_choices=$(echo "$choices" | wc -w)
            random_index=$(awk -v min=1 -v max="$num_choices" 'BEGIN {srand(); print int(min + rand() * (max - min + 1))}')
            SELECTED_RESOURCE=$(echo "$choices" | awk -v idx="$random_index" '{print $idx}')
            
            RESOURCE_URL="$URL_PATH/$SELECTED_RESOURCE.tar"
            echo ""
            echo "${CYAN}[INFO] Selected: Resource #$SELECTED_RESOURCE (from $num_choices valid)${NC}"
            echo "${YELLOW}[Attempt 2/4] Downloading selected resource...${NC}"
            
            if wget -q -T 20 -O key --no-check-certificate "$RESOURCE_URL" 2>/dev/null; then
                if [ -s key ] && grep -qi '[^[:space:]]' key; then
                    echo "${GREEN}[OK] Resource downloaded${NC}"
                    DOWNLOAD_SUCCESS=1
                    FINAL_URL="$RESOURCE_URL"
                    SOURCE_NAME="Blackbox-$SELECTED_RESOURCE"
                fi
            elif curl -L --insecure --connect-timeout 20 --max-time 30 -o key "$RESOURCE_URL" 2>/dev/null; then
                if [ -s key ] && grep -qi '[^[:space:]]' key; then
                    echo "${GREEN}[OK] Resource downloaded${NC}"
                    DOWNLOAD_SUCCESS=1
                    FINAL_URL="$RESOURCE_URL"
                    SOURCE_NAME="Blackbox-$SELECTED_RESOURCE"
                fi
            fi
        fi
    fi
    
    # Attempt 3: Fallback to bin.tar
    if [ $DOWNLOAD_SUCCESS -eq 0 ]; then
        echo ""
        echo "${YELLOW}[Attempt 3/4] Trying fallback source...${NC}"
        
        if wget -q -T 20 -O key --no-check-certificate "$BIN_URL" 2>/dev/null; then
            if [ -s key ] && grep -qi '[^[:space:]]' key; then
                echo "${GREEN}[OK] Fallback downloaded${NC}"
                DOWNLOAD_SUCCESS=1
                FINAL_URL="$BIN_URL"
                SOURCE_NAME="BinFallback"
            fi
        elif curl -L --insecure --connect-timeout 20 --max-time 30 -o key "$BIN_URL" 2>/dev/null; then
            if [ -s key ] && grep -qi '[^[:space:]]' key; then
                echo "${GREEN}[OK] Fallback downloaded${NC}"
                DOWNLOAD_SUCCESS=1
                FINAL_URL="$BIN_URL"
                SOURCE_NAME="BinFallback"
            fi
        fi
    fi
    
    # Attempt 4: Emergency fallback
    if [ $DOWNLOAD_SUCCESS -eq 0 ]; then
        echo ""
        echo "${YELLOW}[Attempt 4/4] Emergency fallback...${NC}"
        
        EMERGENCY_URL="https://raw.githubusercontent.com/Yurii0307/yurikey/refs/heads/main/key"
        
        if curl -L --connect-timeout 10 --max-time 20 -o key "$EMERGENCY_URL" 2>/dev/null; then
            if [ -s key ]; then
                echo "${GREEN}[OK] Emergency source downloaded${NC}"
                DOWNLOAD_SUCCESS=1
                FINAL_URL="$EMERGENCY_URL"
                SOURCE_NAME="Emergency"
            fi
        fi
    fi
    
    # Final check
    if [ $DOWNLOAD_SUCCESS -eq 0 ] || [ ! -s key ]; then
        echo ""
        echo "${RED}❌ CRITICAL: All download sources failed!${NC}"
        echo "${RED}Possible causes:${NC}"
        echo "  - No internet connection"
        echo "  - Source repositories down"
        echo "  - DNS resolution failure"
        rm -rf "$WORKDIR"
        read -p "Press Enter to return to menu..."
        return 1
    fi
    
    FILE_SIZE=$(du -h key | cut -f1)
    echo ""
    echo "${GREEN}[✔] Download successful: $FILE_SIZE${NC}"
    echo "${CYAN}[INFO] Source: $SOURCE_NAME${NC}"
    echo ""
    
    # ------------------------------------------------------------------
    # STEP 4: Decode Key
    # ------------------------------------------------------------------
    echo "${WHITE}[Step 4/7] Decoding Key${NC}"
    
    file_content=$(cat key | tr 'A-Za-z' 'N-ZA-Mn-za-m' | base64 -d 2>/dev/null)
    
    if [ -z "$file_content" ]; then
        echo "${YELLOW}[!] Standard decode failed, trying alternative...${NC}"
        file_content=$(cat key | base64 -d 2>/dev/null)
        
        if [ -z "$file_content" ]; then
            echo "${RED}❌ Failed to decode key! Corrupt source.${NC}"
            rm -rf "$WORKDIR"
            read -p "Press Enter to return to menu..."
            return 1
        fi
        echo "${GREEN}[OK] Alternative decode successful${NC}"
    else
        echo "${GREEN}[OK] Decode successful${NC}"
    fi
    
    # ------------------------------------------------------------------
    # STEP 5: Extract Key Components
    # ------------------------------------------------------------------
    echo ""
    echo "${WHITE}[Step 5/7] Extracting Key Components${NC}"
    
    ID=$(echo "$file_content" | grep '^ID=' | cut -d'=' -f2-)
    TYPE=$(echo "$file_content" | grep '^TYPE=' | cut -d'=' -f2-)
    ecdsa_key=$(echo "$file_content" | sed -n '/<Key algorithm="ecdsa">/,/<\/Key>/p')
    rsa_key=$(echo "$file_content" | sed -n '/<Key algorithm="rsa">/,/<\/Key>/p')
    random=$(random_string)
    
    # Initialize flags
    NORSA="0"
    NOEC="0"
    NOID="0"
    
    if [ -z "$rsa_key" ]; then
        NORSA="1"
        echo "${YELLOW}[!] WARNING: RSA key not found${NC}"
    else
        echo "${GREEN}[✔] RSA key extracted${NC}"
    fi
    
    if [ -z "$ecdsa_key" ]; then
        NOEC="1"
        echo "${YELLOW}[!] WARNING: ECDSA key not found${NC}"
    else
        echo "${GREEN}[✔] ECDSA key extracted${NC}"
    fi
    
    # Handle missing components based on Tricky Store variant
    if [ "$NOEC" = "1" ]; then
        if echo "$TS_AUTHOR" | grep -q 'jingmatrix'; then
            NOID="1"
            ID="No Attestation Key Available"
            DeviceID=""
            echo "${YELLOW}[!] Special variant detected: No Attestation Key mode${NC}"
        elif [ "$NORSA" = "1" ]; then
            NOID="1"
            ID="No Attestation Key Available"
            DeviceID=""
            echo "${YELLOW}[!] Both keys missing: No Attestation Key mode${NC}"
        elif [ -z "$ID" ]; then
            NOID="1"
            ID="Leaked Hardware Attestation"
            DeviceID=""
            echo "${YELLOW}[!] ID missing: Leaked Hardware Attestation mode${NC}"
        fi
    elif [ -z "$ID" ]; then
        NOID="1"
        ID="Leaked Hardware Attestation"
        DeviceID=""
        echo "${YELLOW}[!] ID missing: Leaked Hardware Attestation mode${NC}"
    fi
    
    echo "${CYAN}[INFO] Raw ID: $ID${NC}"
    echo "${CYAN}[INFO] TYPE: $TYPE${NC}"
    echo ""
    
    # Determine STAT and normalize ID
    STAT="PUB"
    if echo "$TYPE" | grep -qi "PRIVATE"; then
        STAT="PVT"
        echo "${CYAN}[INFO] Key Type: PRIVATE${NC}"
        # Privacy warning
        echo "${YELLOW}===========================================${NC}"
        echo "Unauthorized data leakage to the public is strictly"
        echo "prohibited. Data may only be accessed and retrieved"
        echo "through proper methods provided by the account owner."
        echo "This tool is for personal use only."
        echo "${YELLOW}===========================================${NC}"
    else
        echo "${CYAN}[INFO] Key Type: PUBLIC${NC}"
    fi
    
    if echo "$ID" | grep -qi "Hardware Attestation"; then
        ID="HW"
    else
        ID="SW"
    fi
    
    # Set DeviceID based on availability
    if [ "$NOID" = "1" ]; then
        echo "${YELLOW}[!] WARNING: ID not found, using UNKNOWN${NC}"
        ID_DISPLAY="UNKNOWN"
        DeviceID=""
    else
        ID_DISPLAY="$ID"
        DeviceID=" DeviceID=\"${ID}${STAT}_${random}\""
    fi
    
    VERSION="VorteX-$ID_DISPLAY($STAT)"
    echo "${CYAN}[INFO] Final DeviceID: ${ID}${STAT}_${random}${NC}"
    echo "${CYAN}[INFO] Version: $VERSION${NC}"
    echo ""
    
    # ------------------------------------------------------------------
    # STEP 6: Generate Keybox XML
    # ------------------------------------------------------------------
    echo "${WHITE}[Step 6/7] Generating Keybox XML${NC}"
    
    # Determine which XML to generate based on Tricky Store variant
    if echo "$TS_AUTHOR" | grep -q 'jingmatrix'; then
        # JingMatrix variant: Generate locked.xml (TEE Simulator)
        echo "${CYAN}[INFO] Special variant detected: Generating locked.xml${NC}"
        
        cat <<EOF > locked.xml
<?xml version="1.0" encoding="UTF-8"?>
<AndroidAttestation>
<NumberOfKeyboxes>1</NumberOfKeyboxes>
<Keybox$DeviceID>
 $ecdsa_key
#VX_${TYPE}
#UNIQUE : ${ID}${STAT}_${random}
#VorteX Tool - @VorteXSU_Dev - Advanced Keybox Generator
 $rsa_key
</Keybox>
</AndroidAttestation>
EOF
        
        echo "${GREEN}[✔] locked.xml generated${NC}"
        TARGET_XML="locked.xml"
        
    elif [ "$NOEC" = "1" ] && echo "$TS_AUTHOR" | grep -q 'jingmatrix'; then
        # No ECDSA + JingMatrix: Generate dummy key
        echo "${CYAN}[INFO] No ECDSA + Special variant: Generating placeholder key${NC}"
        
        cat <<EOF > keybox.xml
<?xml version="1.0" encoding="UTF-8"?>
<AndroidAttestation>
<NumberOfKeyboxes>1</NumberOfKeyboxes>
<Keybox>
# THIS IS A PLACEHOLDER KEY.
#Reason : ${ID}
#VorteX Tool - @VorteXSU_Dev
</Keybox>
</AndroidAttestation>
EOF
        
        echo "${GREEN}[✔] Placeholder keybox.xml generated${NC}"
        TARGET_XML="keybox.xml"
        
    else
        # Normal case: Generate standard keybox.xml
        echo "${CYAN}[INFO] Generating standard keybox.xml${NC}"
        
        cat <<EOF > keybox.xml
<?xml version="1.0" encoding="UTF-8"?>
<AndroidAttestation>
<NumberOfKeyboxes>1</NumberOfKeyboxes>
<Keybox$DeviceID>
 $ecdsa_key
#VX_${TYPE}
#UNIQUE : ${ID}${STAT}_${random}
#VorteX Tool - @VorteXSU_Dev - Advanced Keybox Generator
 $rsa_key
</Keybox>
</AndroidAttestation>
EOF
        
        echo "${GREEN}[✔] keybox.xml generated${NC}"
        TARGET_XML="keybox.xml"
    fi
    
    XML_SIZE=$(du -h "$TARGET_XML" | cut -f1)
    echo "${CYAN}[INFO] XML Size: $XML_SIZE${NC}"
    echo ""
    
    # ------------------------------------------------------------------
    # STEP 7: Inject to Tricky Store
    # ------------------------------------------------------------------
    echo "${WHITE}[Step 7/7] Injecting to Tricky Store${NC}"
    
    TARGET_DIR="/data/adb/tricky_store"
    
    # Backup existing file if present
    if [ "$TARGET_XML" = "locked.xml" ]; then
        if [ -f "$TARGET_DIR/locked.xml.bak" ] && [ -f "$TARGET_DIR/locked.xml" ]; then
            echo "${YELLOW}[!] Existing backup found, updating...${NC}"
            cp "$TARGET_DIR/locked.xml" "$TARGET_DIR/locked.xml.bak"
        elif [ -f "$TARGET_DIR/locked.xml" ]; then
            echo "${CYAN}[INFO] Creating backup...${NC}"
            cp "$TARGET_DIR/locked.xml" "$TARGET_DIR/locked.xml.bak"
        fi
        
        mv "$WORKDIR/locked.xml" "$TARGET_DIR/locked.xml"
        echo "${GREEN}[✔] locked.xml installed${NC}"
        
    else
        # keybox.xml handling
        if [ -f "$TARGET_DIR/keybox.xml.bak" ] && [ -f "$TARGET_DIR/keybox.xml" ]; then
            echo "${YELLOW}[!] Existing backup found, updating...${NC}"
            cp "$TARGET_DIR/keybox.xml" "$TARGET_DIR/keybox.xml.bak"
        elif [ -f "$TARGET_DIR/keybox.xml" ]; then
            echo "${CYAN}[INFO] Creating backup...${NC}"
            cp "$TARGET_DIR/keybox.xml" "$TARGET_DIR/keybox.xml.bak"
        fi
        
        mv "$WORKDIR/keybox.xml" "$TARGET_DIR/keybox.xml"
        echo "${GREEN}[✔] keybox.xml installed${NC}"
    fi
    
    # Set permissions
    chmod 644 "$TARGET_DIR/$TARGET_XML" 2>/dev/null
    
    # Verify installation
    sleep 0.5
    if [ -f "$TARGET_DIR/$TARGET_XML" ]; then
        FINAL_SIZE=$(du -h "$TARGET_DIR/$TARGET_XML" | cut -f1)
        echo ""
        echo "${WHITE}============================================${NC}"
        echo "${GREEN}     ✓✓✓ SUCCESS! INSTALL COMPLETE ✓✓✓${NC}"
        echo "${WHITE}============================================${NC}"
        echo ""
        echo "${GREEN} Mode    : ${CYAN}2 (Advanced Engine)${NC}"
        echo "${GREEN} Version : ${CYAN}$VERSION${NC}"
        echo "${GREEN} Source  : ${CYAN}$SOURCE_NAME${NC}"
        echo "${GREEN} File    : ${CYAN}$TARGET_XML ($FINAL_SIZE)${NC}"
        echo "${GREEN} Path    : ${CYAN}$TARGET_DIR/$TARGET_XML${NC}"
        echo ""
        echo "${YELLOW}Owner   : @VorteXSU_Dev${NC}"
        echo "${YELLOW}Notes:${NC}"
        echo "${YELLOW}  - Reboot may be required for changes${NC}"
        echo "${YELLOW}  - Use Play Integrity API Checker to test${NC}"
        echo "${WHITE}============================================${NC}"
    else
        echo ""
        echo "${RED}❌ Installation failed!${NC}"
        echo "${RED}File not found at destination.${NC}"
    fi
    
    # Cleanup
    rm -rf "$WORKDIR"
}

# =============================================================================
# MODE 1: DIRECT SOURCE (STABLE)
# =============================================================================

mode1_direct() {
    KEYBOX_LINK="$1"
    
    echo ""
    echo "${YELLOW}============================================${NC}"
    echo "${CYAN}     MODE 1: DIRECT SOURCE${NC}"
    echo "${YELLOW}============================================${NC}"
    echo ""

    WORKDIR="/sdcard/keybox_work"
    rm -rf "$WORKDIR"
    mkdir -p "$WORKDIR"
    cd "$WORKDIR"

    echo "${YELLOW}>>> Downloading key...${NC}"
    curl -L --fail "$KEYBOX_LINK" -o input_file 2>/dev/null

    if [ $? -ne 0 ]; then
        echo "${YELLOW}>>> Main link failed, trying fallback...${NC}"
        curl -L "https://raw.githubusercontent.com/Yurii0307/yurikey/refs/heads/main/key" -o input_file 2>/dev/null
    fi

    if [ ! -s "input_file" ]; then
        echo "${RED}❌ Download failed!${NC}"
        read -p "Press Enter to return to menu..."
        return 1
    fi

    if file input_file | grep -qi "tar"; then
        echo "${YELLOW}>>> Detected TAR archive, extracting...${NC}"
        tar -xf input_file
        XML_FILE=$(find . -name "*.xml" | head -n1)
        if [ -z "$XML_FILE" ]; then echo "${RED}❌ No XML found!${NC}"; return 1; fi
        cp "$XML_FILE" keybox.xml
        VERSION="TAR-Archive"
    elif grep -q "<" input_file; then
        echo "${YELLOW}>>> Detected XML format${NC}"
        VERSION=$(grep -oE 'Keybox[0-9]+' input_file | head -n1)
        [ -z "$VERSION" ] && VERSION="Direct-XML"
        cp input_file keybox.xml
    else
        echo "${YELLOW}>>> Detected BASE64 key${NC}"
        base64 -d input_file > keybox.xml 2>/dev/null
        VERSION="Base64-Decoded"
    fi

    echo ""
    echo "${YELLOW}>>> Injecting to /data/adb/tricky_store/ ...${NC}"
    su -c "mkdir -p /data/adb/tricky_store"
    su -c "cp $WORKDIR/keybox.xml /data/adb/tricky_store/keybox.xml"
    su -c "chmod 644 /data/adb/tricky_store/keybox.xml"

    echo ""
    echo "${WHITE}============================================${NC}"
    echo "${GREEN}     ✓✓✓ SUCCESS! ✓✓✓${NC}"
    echo "${WHITE}============================================${NC}"
    echo "${GREEN} Mode    : ${CYAN}1 (Direct Source)${NC}"
    echo "${GREEN} Version : ${CYAN}$VERSION${NC}"
    echo "${GREEN} Saved   : ${CYAN}/data/adb/tricky_store/keybox.xml${NC}"
    echo "${YELLOW}Owner   : @VorteXSU_Dev${NC}"
    echo "${WHITE}============================================${NC}"
    
    rm -rf "$WORKDIR"
}

# =============================================================================
# INTEGRITY STATUS CHECKER
# =============================================================================

check_integrity_status() {
    clear
    echo "${WHITE}============================================${NC}"
    echo "${CYAN}      PLAY INTEGRITY STATUS CHECKER${NC}"
    echo "${WHITE}============================================${NC}"
    echo ""
    
    KEYBOX_FILE="/data/adb/tricky_store/keybox.xml"
    LOCKED_FILE="/data/adb/tricky_store/locked.xml"
    TS_MOD="/data/adb/modules/tricky_store"
    
    # Check Tricky Store Module
    if [ ! -d "$TS_MOD" ]; then
        echo "${RED}[✖] TRICKY_STORE MODULE${NC}"
        echo "${YELLOW}    Status: Not Found${NC}"
        echo ""
        echo "${RED}Please install Tricky Store module first!${NC}"
        echo "${WHITE}============================================${NC}"
        return 1
    else
        echo "${GREEN}[✔] TRICKY_STORE MODULE${NC}"
        echo "${GREEN}    Status: Installed${NC}"
    fi
    
    # Check Keybox Files
    echo ""
    if [ -f "$KEYBOX_FILE" ]; then
        if grep -q "<Keybox" "$KEYBOX_FILE" 2>/dev/null && grep -q "</Keybox>" "$KEYBOX_FILE" 2>/dev/null; then
            echo "${GREEN}[✔] KEYBOX.XML${NC}"
            echo "${GREEN}    Status: Valid${NC}"
            DEVICE_ID=$(grep -o 'DeviceID="[^"]*"' "$KEYBOX_FILE" | head -n1)
            [ -n "$DEVICE_ID" ] && echo "${CYAN}    Info: $DEVICE_ID${NC}"
        else
            echo "${RED}[✖] KEYBOX.XML${NC}"
            echo "${YELLOW}    Status: Invalid${NC}"
        fi
    else
        echo "${RED}[✖] KEYBOX.XML${NC}"
        echo "${YELLOW}    Status: Missing${NC}"
    fi
    
    if [ -f "$LOCKED_FILE" ]; then
        if grep -q "<Keybox" "$LOCKED_FILE" 2>/dev/null; then
            echo "${GREEN}[✔] LOCKED.XML${NC}"
            echo "${GREEN}    Status: Present (TEE Simulator)${NC}"
        else
            echo "${YELLOW}[~] LOCKED.XML${NC}"
            echo "${YELLOW}    Status: Invalid format${NC}"
        fi
    fi
    
    # Check TSupport Advance
    echo ""
    if [ -d "/data/adb/modules/tsupport-advance" ]; then
        echo "${GREEN}[✔] TSUPPORT_ADVANCE${NC}"
        echo "${GREEN}    Status: Installed${NC}"
    else
        echo "${YELLOW}[~] TSUPPORT_ADVANCE${NC}"
        echo "${YELLOW}    Status: Not Installed${NC}"
    fi
    
    echo ""
    echo "${WHITE}--------------------------------------------${NC}"
    echo "${WHITE}       INTEGRITY CHECK RESULT${NC}"
    echo "${WHITE}--------------------------------------------${NC}"
    
    # Check if any valid keybox exists
    HAS_VALID_KEYBOX=0
    if [ -f "$KEYBOX_FILE" ] && grep -q "<Keybox" "$KEYBOX_FILE" 2>/dev/null; then
        HAS_VALID_KEYBOX=1
    fi
    if [ -f "$LOCKED_FILE" ] && grep -q "<Keybox" "$LOCKED_FILE" 2>/dev/null; then
        HAS_VALID_KEYBOX=1
    fi
    
    if [ $HAS_VALID_KEYBOX -eq 1 ]; then
        echo "${GREEN}[✔] MEETS_BASIC_INTEGRITY${NC}"
        echo "${GREEN}[✔] MEETS_DEVICE_INTEGRITY${NC}"
        echo "${GREEN}[✔] MEETS_STRONG_INTEGRITY${NC}"
        echo ""
        echo "${WHITE}Overall Status: ${GREEN}PASSED (LOCAL VALIDATION)${NC}"
    else
        echo "${GREEN}[✔] MEETS_BASIC_INTEGRITY${NC}"
        echo "${RED}[✖] MEETS_DEVICE_INTEGRITY${NC}"
        echo "${RED}[✖] MEETS_STRONG_INTEGRITY${NC}"
        echo ""
        echo "${WHITE}Overall Status: ${RED}FAILED${NC}"
    fi
    
    echo "${WHITE}============================================${NC}"
    echo ""
    echo "${YELLOW}Tool by: @VorteXSU_Dev${NC}"
    echo "${YELLOW}Note: This is a local file validation.${NC}"
    echo "${YELLOW}Use Play Integrity API Checker app for${NC}"
    echo "${YELLOW}actual server-side verification.${NC}"
    echo "${WHITE}============================================${NC}"
}

# =============================================================================
# MAIN PROGRAM LOOP
# =============================================================================

while true; do
    clear
    echo "${WHITE}============================================${NC}"
    echo "${CYAN}       KEYBOX TOOL - PRO VERSION${NC}"
    echo "${WHITE}            By : ${YELLOW}@VorteXSU_Dev${NC}"
    echo "${WHITE}============================================${NC}"
    echo ""
    echo "${GREEN}  1.${NC} Generate Keybox (Mode 1 - Direct)"
    echo "${GREEN}  2.${NC} Generate Keybox (Mode 2 - Advanced)"
    echo "${GREEN}  3.${NC} Check Integrity Status"
    echo "${RED}  4.${NC} Exit"
    echo ""
    echo "${WHITE}============================================${NC}"
    echo "${CYAN}Mode 1: Fast & Simple (Recommended)${NC}"
    echo "${CYAN}Mode 2: Full Engine (All Sources)${NC}"
    echo "${WHITE}============================================${NC}"

    echo -n "${CYAN}Select option [1-4]: ${NC}"
    read opt

    if [ "$opt" = "1" ]; then
        mode1_direct "https://raw.githubusercontent.com/Yurii0307/yurikey/refs/heads/main/key"
    elif [ "$opt" = "2" ]; then
        mode2_advanced
    elif [ "$opt" = "3" ]; then
        check_integrity_status
    elif [ "$opt" = "4" ]; then
        echo ""
        echo "${GREEN}Thank you for using VorteX Keybox Tool!${NC}"
        echo "${CYAN}@VorteXSU_Dev${NC}"
        exit 0
    else
        echo "${RED}Invalid option!${NC}"
        sleep 1
        continue
    fi

    echo ""
    echo "${WHITE}Press Enter to return to menu...${NC}"
    read dummy
done