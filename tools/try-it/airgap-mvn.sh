#!/bin/sh
# Runs any Maven build's tests under the airgap agent, without changing that build:
#
#   /path/to/airgap-mvn.sh test            (any Maven goals and settings, as you'd give mvn)
#
# It fetches the airgap Maven extension, the agent and the JUnit listener with Maven's own dependency plugin, into
# your local Maven repository, and then runs the build's ./mvnw (or mvn, if it has none) with the three jars named in
# -Dmaven.ext.class.path. The extension adds the agent to every module's Surefire, Failsafe, spring-boot:run and
# exec:exec runs, in memory; the build's poms don't change. Each run's report goes to
# <module>/target/airgap/<plugin>-<execution>/calls.jsonl; no report means no risky calls.
#
# Settings, as environment variables:
#   AIRGAP_VERSION     the version to fetch
#   AIRGAP_REPOSITORY  a repository to fetch from as well as Maven Central (a company's own, say)
#   AIRGAP_JARS        a folder that already holds the three jars, so nothing is fetched
set -eu

version="${AIRGAP_VERSION:-0.4.0}"
group="io.github.fiftieshousewife.airgap"

if [ -f ./mvnw ]; then
    mvn="sh ./mvnw"
else
    mvn="mvn"
fi

if [ -n "${AIRGAP_JARS:-}" ]; then
    jars="$AIRGAP_JARS"
else
    jars="${TMPDIR:-/tmp}/airgap-maven-$version"
    mkdir -p "$jars"
    for artifact in airgap-maven-extension airgap-agent airgap-junit; do
        if [ ! -f "$jars/$artifact-$version.jar" ]; then
            # shellcheck disable=SC2086
            $mvn -B -q org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
                "-Dartifact=$group:$artifact:$version" "-DoutputDirectory=$jars" \
                ${AIRGAP_REPOSITORY:+"-DremoteRepositories=$AIRGAP_REPOSITORY"}
        fi
    done
fi

path=""
for artifact in airgap-maven-extension airgap-agent airgap-junit; do
    jar=$(ls "$jars"/"$artifact"-*.jar 2>/dev/null | head -n 1)
    if [ -z "$jar" ]; then
        echo "airgap: no $artifact jar in $jars" >&2
        exit 1
    fi
    path="${path:+$path:}$jar"
done

# shellcheck disable=SC2086
exec $mvn "-Dmaven.ext.class.path=$path" "$@"
