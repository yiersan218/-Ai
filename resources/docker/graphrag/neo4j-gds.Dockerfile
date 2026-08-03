FROM neo4j:5.26-community

# Neo4j's NEO4J_PLUGINS downloader fetches GDS again on every container start.
# Bake the verified compatible version into a reusable local image instead.
ARG GDS_VERSION=2.13.11
RUN wget -q --timeout=300 --tries=5 \
      --output-document=/var/lib/neo4j/plugins/graph-data-science.jar \
      "https://graphdatascience.ninja/neo4j-graph-data-science-${GDS_VERSION}.jar" \
    && test -s /var/lib/neo4j/plugins/graph-data-science.jar \
    && chmod 0644 /var/lib/neo4j/plugins/graph-data-science.jar
