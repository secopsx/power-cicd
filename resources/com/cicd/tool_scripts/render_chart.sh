#!/bin/bash

_src=$1
_dst=$2
[ -d $_src -a -d $_dst ]||{
    echo "${_error:-error} - src or dst dose not exist";
    exit 1;
}
touch $_dst/.tmp
[ $? -ne 0  ]&&{
    echo "${_error:-error} - can not write to dst: ${_dst}";
    exit 2;
}
rm -f $_dst/* > /dev/null 2>&1

echo "${_info:-info} - start to render chart for ${CI_VESYNC_APP_NAME}"
for _item in `find $_src -type f |awk -F"$_src/" '{print $2}'`
do
    [ " ${_item}" != " Chart.yaml" -a " ${_item}" != " values.yaml" ]&&{
    #[ " ${_item}" != " Chart.yaml" ]&&{
        # Only render  Chart.yaml and values.yaml, and copy other files to _dst directly
        echo "${_debug} - skip render template: ${_item}, copy it to ${_dst}"
        [ `basename ${_item}` != "${_item}" ]&&mkdir -p ${_dst}/${_item%/*}
        cp $_src/${_item} ${_dst}/${_item}
        continue
    }
    _item_content=$(< $_src/${_item})
    # item 中有目录，要先尝试在_dst 中创建目录
    [ `basename ${_item}` != "${_item}" ]&&mkdir -p ${_dst}/${_item%/*}
    echo "${_info:-info} - render item: ${_dst}/${_item}"
    eval "cat <<EOF
${_item_content}
EOF" > ${_dst}/${_item}
done