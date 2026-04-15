using System.IO;
using Concentus.Enums;
using Concentus.Structs;
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
        var oggWriter = new OpusOggWriteStream(encoder, outputStream);

        int offset = 0;
        while (offset + frameSizeSamples <= samples.Length)
        {
            oggWriter.WriteSamples(samples, offset, frameSizeSamples);
            offset += frameSizeSamples;
        }

        if (offset < samples.Length)
        {
            int remainingCount = samples.Length - offset;
            var remaining = new short[remainingCount];
            Array.Copy(samples, offset, remaining, 0, remainingCount);
            oggWriter.WriteSamples(remaining, 0, remainingCount);
        }

        oggWriter.Finish();
        return outputStream.ToArray();
    }
}
