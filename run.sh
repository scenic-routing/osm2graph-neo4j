#!/usr/bin/env bash

# config variables
OSMFILEPATH=/mnt/c/Users/Taylor/Downloads/SanFrancisco.osm
GRAPHDBPATH=/var/lib/neo4j/data/databases/graph.db

echo "Welcome to the osm2graph-neo4j Execution Menu"
PS3='Which task would you like to perform? '
options=("Default - All Load Activities" "Load OSM Nodes" "Load OSM Ways" "Create Node Index in Graph DB" "Reset Target Graph DB" "Quit")
select opt in "${options[@]}"
do
    case $opt in
        "Default - All Load Activities")
            mvn exec:java -DgraphDb="$GRAPHDBPATH" -DosmFile="$OSMFILEPATH"
            break
            ;;
        "Load OSM Nodes")
            mvn exec:java -DgraphDb="$GRAPHDBPATH" -DosmFile="$OSMFILEPATH" -Daction=loadnodes
            break
            ;;
        "Load OSM Ways")
            mvn exec:java -DgraphDb="$GRAPHDBPATH" -DosmFile="$OSMFILEPATH" -Daction=loadways
            break
            ;;
        "Create Node Index in Graph DB")
            mvn exec:java -DgraphDb="$GRAPHDBPATH" -DosmFile="$OSMFILEPATH" -Daction=createnodeindex
            break
            ;;
        "Reset Target Graph DB")
            mvn exec:java -DgraphDb="$GRAPHDBPATH" -DosmFile="$OSMFILEPATH" -Daction=resetgraphdb
            break
            ;;
        "Quit")
            break
            ;;
        *) echo "invalid option: $REPLY - please retry";
    esac
done