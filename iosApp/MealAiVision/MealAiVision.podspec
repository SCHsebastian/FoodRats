Pod::Spec.new do |s|
  s.name             = 'MealAiVision'
  s.version          = '0.1.0'
  s.summary          = 'Dynamic-framework wrapper isolating MediaPipe Tasks Vision from the app link.'
  s.description      = <<-DESC
    Encapsulates the statically-vendored MediaPipe Tasks Vision symbols (GTMSessionFetcher, gRPC,
    RE2, protobuf) inside a dynamic framework so they do not collide with Firebase's copies at the
    app-binary link stage. The app links this framework dynamically; MediaPipe is linked statically
    inside it.
  DESC
  s.homepage         = 'https://es.schsebastian.foodrats'
  s.license          = { :type => 'Proprietary' }
  s.author           = { 'FoodRats' => 'schsebastiancardonahenao@gmail.com' }
  s.source           = { :path => '.' }
  s.platform         = :ios, '18.2'
  s.swift_version    = '5.0'

  s.source_files     = 'Sources/**/*.swift'

  # Pulls MediaPipe's static vendored xcframework into THIS dynamic framework, keeping its
  # vendored GTMSessionFetcher/gRPC/RE2 symbols out of the app binary.
  s.dependency 'MediaPipeTasksVision', '~> 0.10.14'
end
