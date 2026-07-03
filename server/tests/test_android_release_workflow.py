from pathlib import Path

import pytest


WORKFLOW = Path(".github/workflows/android-release.yml")
APP_BUILD = Path("packaging/android/app/build.gradle")

pytestmark = pytest.mark.no_db


def _step_block(text: str, step_name: str) -> str:
    marker = f"      - name: {step_name}"
    start = text.index(marker)
    next_step = text.find("\n      - name:", start + len(marker))
    if next_step == -1:
        return text[start:]
    return text[start:next_step]


def test_github_release_uploads_only_phone_apk_for_obtainium():
    text = WORKFLOW.read_text()
    upload_step = _step_block(text, "Upload release assets")

    assert "dist/*.apk" not in upload_step
    assert '"dist/forestmate-android-${asset_tag}.apk"' in upload_step
    assert '"dist/forestmate-android-${asset_tag}.sha256"' in upload_step
    assert '"dist/forestmate-android-${asset_tag}.sigstore.json"' in upload_step
    assert "forestmate-wear-${asset_tag}.apk" not in upload_step


def test_release_checksum_and_provenance_are_phone_apk_scoped():
    text = WORKFLOW.read_text()
    build_step = _step_block(text, "Build signed phone and watch APKs")
    provenance_step = _step_block(text, "Generate build provenance")

    assert 'shasum -a 256 "dist/forestmate-android-${asset_tag}.apk"' in build_step
    assert "dist/*.apk" not in provenance_step
    assert "packaging/android/dist/forestmate-android-*.apk" in provenance_step


def test_phone_release_build_enables_r8_and_resource_shrinking():
    text = APP_BUILD.read_text()

    assert "minifyEnabled true" in text
    assert "shrinkResources true" in text
    assert "getDefaultProguardFile('proguard-android-optimize.txt')" in text
    assert "'proguard-rules.pro'" in text


def test_release_workflow_preserves_r8_mapping_artifact():
    text = WORKFLOW.read_text()
    build_step = _step_block(text, "Build signed phone and watch APKs")
    artifact_step = _step_block(text, "Upload workflow artifact")
    upload_step = _step_block(text, "Upload release assets")

    assert "app/build/outputs/mapping/release/mapping.txt" in build_step
    assert '"dist/forestmate-android-${asset_tag}-mapping.txt"' in build_step
    assert "packaging/android/dist/*mapping*.txt" in artifact_step
    assert "mapping.txt" not in upload_step
