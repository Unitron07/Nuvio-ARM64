module.exports.getStreams = async function(tmdbId, mediaType) {
    await Promise.resolve();
    var response = await fetch('BASE_URL/stream/movie/stage3%3Avideo.json');
    if (!response.ok) throw new Error('Controlled fixture HTTP failure');
    var payload = await response.json();
    return payload.streams.slice(0, 2).map(function(stream) {
        return {title: stream.name, url: stream.url,
            headers: stream.behaviorHints.proxyHeaders.request, provider: 'Stage 3 Fixture'};
    });
};
