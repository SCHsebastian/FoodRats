# Pins the Fastlane toolchain so CI and local runs use the same version.
# Install with: bundle install
# Run a lane with: bundle exec fastlane <platform> <lane>
source "https://rubygems.org"

gem "fastlane", "~> 2.227"

# Ruby 3.4+/4.0 removed several libraries from the default gems, so fastlane
# fails to load them (e.g. `cannot load such file -- ostruct`). The self-hosted
# macOS runner uses Homebrew Ruby 4.x — declare the extracted stdlib gems so
# they're bundled. Harmless on older Ruby (already present).
gem "ostruct"
gem "logger"
gem "benchmark"
gem "bigdecimal"
gem "mutex_m"
gem "csv"
gem "fiddle"
gem "abbrev"
