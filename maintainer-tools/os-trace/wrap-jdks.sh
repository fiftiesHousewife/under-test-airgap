#!/bin/sh
# For CI on Linux: makes, for each JDK the agent supports, a folder whose bin/java starts that JDK's java under strace.
# Gradle is then pointed at those folders with -Pairgap.jdk<version>=<folder>/jdk<version>, so the agent's own tests,
# and every process they start, are recorded; checkOsTrace compares the record with the agent's reports.
#
#   maintainer-tools/os-trace/wrap-jdks.sh <folder for the wrappers> <folder for the traces>
#
# Each JDK is read from $JAVA_HOME_<version>_X64, as actions/setup-java sets it.
set -eu
WRAPPERS="$1"
TRACES="$2"
for version in 8 11 17 21 25; do
  home="$(printenv "JAVA_HOME_${version}_X64")"
  mkdir -p "$WRAPPERS/jdk$version/bin" "$TRACES/jdk$version"
  cat > "$WRAPPERS/jdk$version/bin/java" <<WRAPPER
#!/bin/sh
exec strace -f -ff -qq -e signal=none -e trace=connect,sendto,sendmsg,execve,openat \\
  -o "$TRACES/jdk$version/trace" "$home/bin/java" "\$@"
WRAPPER
  chmod +x "$WRAPPERS/jdk$version/bin/java"
done
