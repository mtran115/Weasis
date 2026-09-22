import json
from pathlib import Path
import sys
import types
import numpy as np
import pytest
import pydicom
from pydicom.dataset import FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import worker
from prepare_pilot import prepare


def series(tmp_path, count=8):
    images = []
    for index in range(count):
        path = tmp_path / f'{index}.dcm'
        meta = FileMetaDataset()
        meta.TransferSyntaxUID = ExplicitVRLittleEndian
        meta.MediaStorageSOPClassUID = pydicom.uid.MRImageStorage
        meta.MediaStorageSOPInstanceUID = f'1.2.3.4.{index+1}'
        ds = FileDataset(str(path), {}, file_meta=meta, preamble=b'\0'*128)
        ds.SOPClassUID = meta.MediaStorageSOPClassUID
        ds.SOPInstanceUID = meta.MediaStorageSOPInstanceUID
        ds.StudyInstanceUID = '1.2.3'
        ds.SeriesInstanceUID = '1.2.3.4'
        ds.FrameOfReferenceUID = '1.2.5'
        ds.Modality = 'MR'; ds.SeriesDescription = 'T2 sagittal'; ds.EchoTime = 90
        ds.Rows = 64; ds.Columns = 64; ds.PixelSpacing = [2, 1]
        ds.ImageOrientationPatient = [0, 1, 0, 0, 0, -1]
        ds.ImagePositionPatient = [index*3, -30, 100]
        ds.SamplesPerPixel = 1; ds.PhotometricInterpretation = 'MONOCHROME2'
        ds.BitsAllocated = 16; ds.BitsStored = 16; ds.HighBit = 15; ds.PixelRepresentation = 0
        ds.PixelData = np.full((64,64), index+1, dtype=np.uint16).tobytes()
        ds.save_as(path, enforce_file_format=True)
        images.append({'studyInstanceUid': '1.2.3', 'reference': {
            'sourceUri': path.as_uri(), 'seriesInstanceUid': str(ds.SeriesInstanceUID),
            'sopInstanceUid': str(ds.SOPInstanceUID), 'sourceFrameIndex': 0}})
    return {'schemaVersion': 1, 'studyKey': 'case', 'studyInstanceUid': '1.2.3', 'images': images}


def test_dicom_volume_preserves_non_square_pixel_geometry_and_sorting(tmp_path):
    request = series(tmp_path)
    request['images'].reverse()
    volume, items, arrays = worker.make_volume(worker.select_series(request))
    # Sorting follows the signed slice normal, not filename/instance number.
    for index, (_, header, _) in enumerate(items):
        for col, row in ((0,0), (10,12)):
            ras = volume.affine @ [col,row,index,1]
            expected = np.array(header.ImagePositionPatient) + [0,col,-row*2]
            np.testing.assert_allclose(np.diag([-1,-1,1]) @ ras[:3], expected)
        assert arrays[index][0,0] == int(header.SOPInstanceUID.split('.')[-1])
    assert volume.header.get_xyzt_units()[0] == 'mm'


def test_wrong_study_and_reference_ids_never_enter_volume(tmp_path):
    request = series(tmp_path)
    request['studyInstanceUid'] = '9.9.9'
    with pytest.raises(ValueError): worker.select_series(request)
    request['studyInstanceUid'] = '1.2.3'
    request['images'][0]['reference']['seriesInstanceUid'] = '9.9'
    with pytest.raises(ValueError): worker.select_series(request)  # Only 7 valid slices remain.


def test_duplicate_sources_are_not_duplicate_slices(tmp_path):
    request = series(tmp_path)
    request['images'] += request['images']
    assert len(worker.select_series(request)) == 8


def test_missing_slice_is_not_silently_interpolated(tmp_path):
    request = series(tmp_path, 10)
    request['images'].pop(4)
    with pytest.raises(ValueError, match='irregular'): worker.make_volume(worker.select_series(request))


def test_non_local_sources_and_multiframe_are_rejected(tmp_path):
    for uri in ('https://example.test/image', 'file://host/image', 'file:///image?token=foo'):
        with pytest.raises(ValueError): worker.local_path(uri)
    request = series(tmp_path)
    for image in request['images']:
        path = worker.local_path(image['reference']['sourceUri'])
        ds = pydicom.dcmread(path); ds.NumberOfFrames = 2; ds.save_as(path)
    with pytest.raises(ValueError): worker.select_series(request)


def test_disc_labels_lps_conversion_and_variant_are_explicit():
    mask = np.zeros((10,10,40), dtype=np.uint16)
    for index, label in enumerate(range(119,125)):
        mask[2:5,2:5,index*5:index*5+3] = label
    affine = np.diag([1,1,-3,1]); affine[2,3] = 150
    points = worker.disc_points(mask, affine)
    assert [p['level'] for p in points] == ['T12-L1','L1-L2','L2-L3','L3-L4','L4-L5','L5-S1']
    assert points[0]['lps'] == [-3,-3,147]
    assert len(worker.assess_points(points)) == 1
    mask[2:5,2:5,30:33] = 125
    variant = worker.disc_points(mask, affine)
    assert variant[-2]['level'] == 'L5-L6' and variant[-1]['level'] == 'L6-S1'
    assert len(worker.assess_points(variant)) > 1
    mask[2:5,2:5,35:38] = 128
    thoracic = worker.disc_points(mask, affine)
    assert thoracic[0]['level'] == 'T12-T13'
    assert any(p['level'] == 'T13-L1' for p in thoracic)


def test_t11_t12_is_retained_as_the_first_of_seven_standard_discs():
    mask = np.zeros((10,10,40), dtype=np.uint16)
    for index, label in enumerate(range(118,125)):
        mask[2:5,2:5,index*5:index*5+3] = label
    affine = np.diag([1,1,-3,1]); affine[2,3] = 150
    raw = worker.disc_points(mask, affine)
    assert [p['level'] for p in raw] == list(worker.STANDARD_LEVELS)
    points, reasons, uncertain = worker.reviewed_numbering_proposal(raw)
    assert not uncertain and len(reasons) == 1
    assert points == raw
    assert points[0]['id'] == 'disc-118'


@pytest.mark.parametrize('levels', [
    ['T11-T12','T12-T13','T13-L1','L1-L2','L2-L3','L3-L4','L4-L5'],
    ['T12-L1','L1-L2','L2-L3','L3-L4','L4-L5','L5-L6','L6-S1'],
    ['T12-L1','L1-L2','L2-L3','L3-L4','L4-L5'],
    ['L2-L3','L1-L2','L3-L4','L4-L5','L5-S1'],
])
def test_uncertainty_hides_the_whole_sequence_without_renaming_raw_predictions(levels):
    raw = [{'id': str(i), 'level': level, 'lps': [0,0,100-i*15]} for i,level in enumerate(levels)]
    original = json.loads(json.dumps(raw))
    points, reasons, uncertain = worker.reviewed_numbering_proposal(raw)
    assert uncertain
    assert all(p['level'] == worker.UNASSIGNED for p in points)
    assert [(p['id'],p['lps']) for p in points] == [(p['id'],p['lps']) for p in raw]
    assert raw == original
    assert 'Numbering uncertain' in reasons[1]
    assert 'T13' not in ' '.join(reasons)


def test_unexpected_vertebral_count_without_visible_variant_disc_still_requires_review():
    raw = [{'id':str(i), 'level':level, 'lps':[0,0,100-i*15]} for i,level in enumerate(worker.STANDARD_LEVELS)]
    points, reasons, uncertain = worker.reviewed_numbering_proposal(raw, unexpected_vertebrae=True)
    assert uncertain and len(reasons) == 2
    assert all(p['level'] == worker.UNASSIGNED for p in points)


def test_bad_landmark_geometry_rejects_proposal():
    with pytest.raises(ValueError): worker.assess_points([])
    points = [{'level': f'L{i}-L{i+1}', 'lps': [0,0,float(i)]} for i in range(1,5)]
    with pytest.raises(ValueError, match='spacing'): worker.assess_points(points)


def test_cache_tracks_source_content_and_preserves_confirmed_data(tmp_path, monkeypatch):
    sources = tmp_path/'sources'; sources.mkdir()
    request = series(sources)
    root = tmp_path/'ai'
    calls = []
    class Pipeline:
        def segment(self, *args, **kwargs):
            calls.append(True)
            return types.SimpleNamespace(success=True, vertebra=types.SimpleNamespace(get_seg_array=lambda: np.zeros((2,2,2)), affine=np.eye(4)))
    monkeypatch.setattr(worker, 'PIPELINE', Pipeline())
    monkeypatch.setitem(sys.modules, 'torch', types.SimpleNamespace(set_num_threads=lambda n: None))
    monkeypatch.setitem(sys.modules, 'spineps', types.SimpleNamespace(SpinepsPipeline=Pipeline, InstanceConfig=lambda **kw: kw))
    monkeypatch.setattr(worker, 'disc_points', lambda *args: [{'id': str(i), 'level': f'L{i}-L{i+1}', 'lps': [9,0,100-i*15]} for i in range(1,5)])
    first = worker.process(request, root)
    assert worker.process(request, root) == first
    assert len(calls) == 1 and first['status'] == 'provisional'
    ds = pydicom.dcmread(sources/'0.dcm'); ds.PixelData = np.full((64,64), 999, np.uint16).tobytes(); ds.save_as(sources/'0.dcm')
    second = worker.process(request, root)
    assert second['proposalId'] != first['proposalId'] and len(calls) == 2
    assert first['reference']['geometry']['frameOfReferenceUid'] == '1.2.5'
    assert first['schemaVersion'] == 2 and first['numberingUncertain']
    assert first['modelPoints'][0]['level'] == 'L1-L2'
    assert all(p['level'] == worker.UNASSIGNED for p in first['points'])
    monkeypatch.setattr(worker, 'MODEL_ID', worker.MODEL_ID+'-new-numbering')
    third = worker.process(request, root)
    assert third['proposalId'] != second['proposalId'] and len(calls) == 3


def test_patient_holdout_is_stable_and_unfinished_cases_leave_active_index(tmp_path):
    archive = tmp_path/'archive'; archive.mkdir(); root = tmp_path/'ai'
    for number in range(10):
        case = archive/str(number); case.mkdir(); (case/'image.dcm').write_bytes(b'test')
        doc = {'annotationComplete': True, 'content': {'exam':'LUMBAR_SPINE', 'studyKey':str(number),
            'context':{'patientId': str(number//2), 'studyInstanceUid':str(number)}},
            'sourceArchive':{'availableSourcesArchived':True,'instances':[{'archivedPath':'image.dcm', 'studyInstanceUid':str(number), 'reference':{}}]}}
        (case/'latest.json').write_text(json.dumps(doc))
    assert prepare(archive, root) == {'development':8,'holdout':2}
    index = json.loads((root/'pilot/index.json').read_text())['cases']
    for patient in {x['patientGroup'] for x in index}:
        assert len({x['partition'] for x in index if x['patientGroup'] == patient}) == 1
    split = (root/'dataset-split-v1.json').read_text()
    doc = json.loads((archive/'0/latest.json').read_text()); doc['annotationComplete'] = False
    (archive/'0/latest.json').write_text(json.dumps(doc)); prepare(archive, root)
    assert (root/'dataset-split-v1.json').read_text() == split
    assert len(json.loads((root/'pilot/index.json').read_text())['cases']) == 9
