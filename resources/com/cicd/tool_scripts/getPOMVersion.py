#!/usr/bin/env python3


import xml.etree.ElementTree as ET
import sys

try:
    pom_tree = ET.parse(sys.argv[1])
    root_elements = pom_tree.getroot()
    for child in root_elements:
        key = child.tag.split('}')[1]
        value = child.text
        if key == "version":
            print(value)
            sys.exit(0)
        else:
            continue
    sys.exit(1)
except Exception as e:
    sys.exit(2)