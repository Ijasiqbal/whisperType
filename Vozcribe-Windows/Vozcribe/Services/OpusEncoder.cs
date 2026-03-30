using Concentus;
using Concentus.Enums;
using Concentus.Oggfile;
using Vozcribe.Models;

namespace Vozcribe.Services;

public static class OpusEncoderService
{
    private const int FrameSizeMs = 20;

    public static byte[] Encode(short[] samples, int sampleRate)
    {
        int frameSizeSamples = sampleRate * FrameSizeMs / 1000;
        var encoder = new OpusEncoder(sampleRate, Constants.Channels, OpusApplication.OPUS_APPLICATION_VOIP);
        encoder.Bitrate = Constants.OpusBitrate;

        using var outputStream = new MemoryStream();
        var oggWriter = new OpusOggWriteStream(encoder, outputStream, sampleRate, Constants.Channels);

        int offset = 0;
        while (offset + frameSizeSamples <= samples.Length)
        {
            var frame = new ReadOnlySpan<short>(samples, offset, frameSizeSamples);
            oggWriter.WriteSamples(frame);
            offset += frameSizeSamples;
        }

        if (offset < samples.Length)
        {
            var remaining = new short[frameSizeSamples];
            Array.Copy(samples, offset, remaining, 0, samples.Length - offset);
            oggWriter.WriteSamples(remaining.AsSpan());
        }

        oggWriter.Finish();
        return outputStream.ToArray();
    }
}
