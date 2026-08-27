package clientcfg

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Cross-language contract fixtures shared with the VPN-TZ Android test suite
// (docs/provenance/fixtures). These tests pin the TzGate side of the profile
// wire format so accidental drift breaks CI on both sides simultaneously.
//
// Note: the Go generator targets the historical 60-field layout; positions 60+
// belong exclusively to newer Android producers and are verified there.

func loadFixture(t *testing.T, name string, wantLen int) []string {
	t.Helper()
	path := filepath.Join("..", "..", "..", "docs", "provenance", "fixtures", name)
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			t.Skipf("fixture corpus not present: %s", path)
		}
		t.Fatalf("read fixture: %v", err)
	}
	var fields []string
	if err := json.Unmarshal(data, &fields); err != nil {
		t.Fatalf("parse %s: %v", path, err)
	}
	if len(fields) != wantLen {
		t.Fatalf("%s: expected %d fields, got %d", name, wantLen, len(fields))
	}
	return fields
}

func TestFullRecordPrefixRoundTripsThroughUri(t *testing.T) {
	fields := loadFixture(t, "record_v43_full.json", 87)
	uri := Encode(arrayOf(fields))
	if !strings.HasPrefix(uri, uriScheme) {
		t.Fatalf("Encode lost scheme prefix")
	}
	got, err := Decode(uri)
	if err != nil {
		t.Fatalf("Decode failed: %v", err)
	}
	for i := 0; i < TotalFields && i < len(fields); i++ {
		if got[i] != fields[i] {
			t.Fatalf("position %d drifted: want %q got %q", i, fields[i], got[i])
		}
	}
}

func TestShortRecordIsAcceptedAndTailLeftEmpty(t *testing.T) {
	fields := loadFixture(t, "record_v17_short.json", 60)
	uri := Encode(arrayOf(fields))
	got, err := Decode(uri)
	if err != nil {
		t.Fatalf("Decode failed: %v", err)
	}
	if got[TotalFields-1] != "" || got[1] != "dnstt" {
		t.Fatal("unexpected decode result for short record")
	}
}

func TestDecodeRejectsNonBase64Payload(t *testing.T) {
	if _, err := Decode(uriScheme + "!!!!not-base64!!!!"); err == nil {
		t.Fatal("expected error for invalid base64 payload")
	}
}

func arrayOf(items []string) [TotalFields]string {
	var out [TotalFields]string
	copy(out[:], items)
	return out
}
