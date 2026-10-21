# Generates original synthetic fixtures. Requires Node.js and FFmpeg on PATH.
$ErrorActionPreference='Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
 node scripts/generate-projection-fixtures.cjs
 Get-ChildItem test-media -Filter '*.ppm' | ForEach-Object {
  ffmpeg -hide_banner -loglevel error -y -loop 1 -i $_.FullName -f lavfi -i 'sine=frequency=330:sample_rate=48000' -t 8 -c:v libx264 -pix_fmt yuv420p -c:a aac (Join-Path $_.DirectoryName ($_.BaseName+'.mp4'))
  ffmpeg -hide_banner -loglevel error -y -i $_.FullName -frames:v 1 (Join-Path $_.DirectoryName ($_.BaseName+'.png'))
  if($LASTEXITCODE -ne 0) {throw 'FFmpeg projection generation failed'}
 }
 ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=960x480:rate=24' -f lavfi -i 'sine=frequency=440:sample_rate=48000' -t 6 -c:v libx264 -pix_fmt yuv420p -c:a aac test-media/grid-360.mp4
 ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=660:duration=6' -c:a libmp3lame test-media/tone.mp3
 ffmpeg -hide_banner -loglevel error -y -i test-media/equirect-180.mp4 -i test-media/sample.srt -c:v copy -c:a copy -c:s mov_text test-media/embedded-subtitles.mp4
 ffmpeg -hide_banner -loglevel error -y -i test-media/equirect-360.png -frames:v 1 test-media/equirect-360.jpg
 Copy-Item test-media/*.png,test-media/*.jpg app/src/androidTest/assets/
 Copy-Item test-media/*.mp4,test-media/*.mp3,test-media/*.srt,test-media/*.vtt,test-media/*.ass,test-media/*.ssa app/src/androidTest/assets/
} finally {Pop-Location}


