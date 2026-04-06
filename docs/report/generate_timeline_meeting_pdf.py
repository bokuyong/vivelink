from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


BASE_DIR = Path("/Users/bogguyong/Downloads/vivelink-main 2")
OUT_DIR = BASE_DIR / "docs" / "report"
OUT_PDF = OUT_DIR / "clodock_timeline_meeting_2026-04-06.pdf"
OUT_PREVIEW = OUT_DIR / "clodock_timeline_meeting_2026-04-06_preview.png"

PAGE_WIDTH = 1240
PAGE_HEIGHT = 1754
MARGIN_X = 92
MARGIN_Y = 92
CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)

BG = "#FFFFFF"
TEXT = "#111111"
MUTED = "#5D646A"
PRIMARY = "#174C54"
SECONDARY = "#2D7A86"
LIGHT = "#E8F0F2"
BORDER = "#C5D5DA"
ACCENT = "#F5FAFB"

FONT_PATH = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
TITLE_FONT = ImageFont.truetype(FONT_PATH, 50)
H1_FONT = ImageFont.truetype(FONT_PATH, 34)
H2_FONT = ImageFont.truetype(FONT_PATH, 28)
BODY_FONT = ImageFont.truetype(FONT_PATH, 24)
SMALL_FONT = ImageFont.truetype(FONT_PATH, 20)


def new_page():
    image = Image.new("RGB", (PAGE_WIDTH, PAGE_HEIGHT), BG)
    draw = ImageDraw.Draw(image)
    return image, draw


def text_width(draw, text, font):
    box = draw.textbbox((0, 0), text, font=font)
    return box[2] - box[0]


def line_height(draw, font):
    box = draw.textbbox((0, 0), "가나다ABC123", font=font)
    return box[3] - box[1]


def wrap_text(draw, text, font, max_width):
    lines = []
    current = ""
    for ch in text:
        candidate = current + ch
        if ch == "\n":
            lines.append(current.rstrip())
            current = ""
            continue
        if text_width(draw, candidate, font) <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current.rstrip())
            current = ch.lstrip()
    if current:
        lines.append(current.rstrip())
    return lines


def draw_paragraph(draw, text, x, y, font=BODY_FONT, fill=TEXT, max_width=CONTENT_WIDTH, gap=10):
    lines = wrap_text(draw, text, font, max_width)
    current_y = y
    lh = line_height(draw, font)
    for line in lines:
        draw.text((x, current_y), line, font=font, fill=fill)
        current_y += lh + gap
    return current_y


def draw_bullets(draw, items, x, y, max_width=CONTENT_WIDTH):
    current_y = y
    for item in items:
        draw.text((x, current_y), "•", font=BODY_FONT, fill=SECONDARY)
        current_y = draw_paragraph(draw, item, x + 28, current_y, BODY_FONT, TEXT, max_width - 28, 8) + 8
    return current_y


def draw_header(draw, title):
    draw.text((MARGIN_X, MARGIN_Y), title, font=H1_FONT, fill=PRIMARY)
    draw.line((MARGIN_X, MARGIN_Y + 52, PAGE_WIDTH - MARGIN_X, MARGIN_Y + 52), fill=LIGHT, width=3)
    return MARGIN_Y + 84


def draw_footer(draw, page_no):
    draw.line((MARGIN_X, PAGE_HEIGHT - 68, PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 68), fill=LIGHT, width=2)
    draw.text((MARGIN_X, PAGE_HEIGHT - 50), f"clodock 진행 현황 정리 | {page_no}", font=SMALL_FONT, fill=MUTED)


def draw_box(draw, box, title, body, title_color=PRIMARY):
    draw.rounded_rectangle(box, radius=22, fill=ACCENT, outline=BORDER, width=2)
    draw.text((box[0] + 24, box[1] + 20), title, font=H2_FONT, fill=title_color)
    draw_paragraph(
        draw,
        body,
        box[0] + 24,
        box[1] + 74,
        BODY_FONT,
        TEXT,
        (box[2] - box[0]) - 48,
        10,
    )


def build_cover():
    image, draw = new_page()
    y = 210
    draw.text((MARGIN_X, y), "clodock 진행 현황 및 타임라인 수정안", font=TITLE_FONT, fill=TEXT)
    y += 90
    draw.text((MARGIN_X, y), "캡스톤디자인 주차별 미팅용 정리", font=H1_FONT, fill=PRIMARY)
    y += 100

    summary_box = (MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 350)
    draw.rounded_rectangle(summary_box, radius=24, fill=ACCENT, outline=BORDER, width=2)
    draw.text((summary_box[0] + 28, summary_box[1] + 24), "핵심 요약", font=H2_FONT, fill=PRIMARY)
    summary = (
        "기존에는 PA03와 Raspberry Pi의 투 트랙으로 프로젝트를 진행할 계획이었으나, "
        "실제 PoC 과정에서 PA03의 성능 한계로 미러링이 끊기는 문제가 확인되었다. "
        "이에 따라 이한솔이 보유 중이던 Radxa를 추가하여 현재는 PA03, Raspberry Pi, Radxa의 "
        "3트랙 병행 구조로 계획을 수정하였다. 앱은 현재 폰과 폰 환경에서 우선 개발 및 테스트를 진행하고 있으며, "
        "토스 송금 자동화를 대표 시나리오로 안정화한 뒤 각 하드웨어 트랙에 맞는 형태로 확장할 예정이다."
    )
    draw_paragraph(draw, summary, summary_box[0] + 28, summary_box[1] + 82, BODY_FONT, TEXT, CONTENT_WIDTH - 56, 12)

    y += 410
    meta = [
        ("작성일", "2026.04.06"),
        ("현재 상태", "폰-폰 기반 앱 프로토타입 개발 및 하드웨어 3트랙 준비"),
        ("현재 핵심 기능", "토스 음성 송금 자동화"),
        ("변경 포인트", "투 트랙 -> 3트랙, 주별 계획 -> 월별 계획"),
    ]
    for label, value in meta:
        draw.text((MARGIN_X, y), label, font=H2_FONT, fill=PRIMARY)
        draw.text((MARGIN_X + 210, y), value, font=BODY_FONT, fill=TEXT)
        y += 56

    note = (
        "본 문서는 첫 발표 자료와 과제계획서를 바탕으로, 실제 진행 과정에서 변경된 내용을 반영하여 "
        "교수님 미팅용 타임라인 형태로 다시 정리한 자료이다."
    )
    draw_paragraph(draw, note, MARGIN_X, y + 40, BODY_FONT, MUTED, CONTENT_WIDTH, 10)
    draw_footer(draw, 1)
    return image


def build_page_two():
    image, draw = new_page()
    y = draw_header(draw, "1. 기존 계획과 변경된 방향")

    left_box = (MARGIN_X, y, 590, 860)
    right_box = (650, y, PAGE_WIDTH - MARGIN_X, 860)

    draw_box(
        draw,
        left_box,
        "기존 계획",
        "기존 발표 자료와 계획서 기준으로는 PA03와 Raspberry Pi의 투 트랙 구조를 전제로 "
        "Sender 앱, Controller 앱, MCP 서버, LLM 연동을 순차적으로 구현한 뒤, "
        "트랙별 성능을 비교하여 최종 데모에 사용할 구성을 결정할 계획이었다.\n\n"
        "또한 진행 관리는 주차별 세부 일정에 맞춰 단계적으로 진행하는 구조였다.",
    )
    draw_box(
        draw,
        right_box,
        "현재 변경 방향",
        "실제 개발을 시작한 뒤 PA03에서 미러링이 자주 끊기는 문제가 확인되었고, "
        "하드웨어 구매 절차 역시 예상보다 길어져 기존 계획을 그대로 유지하기 어려웠다.\n\n"
        "이에 따라 이한솔이 보유 중이던 Radxa를 추가하여 3트랙 병행 구조로 수정했고, "
        "일정 관리도 주별 계획 대신 월별 계획 중심으로 다시 정리하였다.",
        title_color=SECONDARY,
    )

    y = 930
    draw.text((MARGIN_X, y), "변경 사유", font=H2_FONT, fill=PRIMARY)
    y += 46
    y = draw_bullets(draw, [
        "하드웨어 구매 절차가 길어져 계획대로 발주와 실험을 바로 진행하기 어려웠음",
        "PA03는 실제 테스트 과정에서 성능 한계로 미러링이 안정적으로 유지되지 않았음",
        "프로젝트를 멈추지 않기 위해 사비로 장비를 우선 확보하고, 대체 보드인 Radxa를 추가하기로 결정했음",
        "미라캐스트는 제어 채널로 사용하기에 적합하지 않다고 판단되어 현재는 보류 상태로 정리했음",
    ], MARGIN_X, y) + 14

    draw.text((MARGIN_X, y), "수정된 핵심 방향", font=H2_FONT, fill=PRIMARY)
    y += 46
    draw_bullets(draw, [
        "PA03, Raspberry Pi, Radxa의 3트랙을 모두 진행하는 것을 목표로 함",
        "앱은 먼저 폰과 폰 환경에서 안정화한 뒤, 각 하드웨어에 맞는 구조로 순차 수정 예정",
        "현재는 토스 자동화처럼 실제 시연 가능한 대표 기능을 먼저 안정화하는 전략으로 진행 중",
    ], MARGIN_X, y)

    draw_footer(draw, 2)
    return image


def build_page_three():
    image, draw = new_page()
    y = draw_header(draw, "2. 현재 앱 및 팀 진행 현황")

    draw.text((MARGIN_X, y), "앱 개발 현황", font=H2_FONT, fill=PRIMARY)
    y += 46
    y = draw_bullets(draw, [
        "Sender 앱에는 음성 인식, 수동 명령 입력, 접근성 상태 확인, 명령 실행 상태 표시 기능이 구현되어 있음",
        "토스 전용 자연어 해석 로직이 들어가 있어 '토스로 누구에게 얼마 송금해' 형태의 문장에서 수취인과 금액을 분리할 수 있음",
        "토스 자동화 흐름은 토스 실행, 송금 진입, 연락처 검색, 상단 결과 선택, 금액 입력, 보내기 버튼 클릭까지 진행되며 마지막 지문 인증은 사용자가 직접 수행하도록 구성됨",
        "Controller 앱 쪽에는 명령 서버, 자동 탐색 응답, 앱 실행, 홈/뒤로 이동, 텍스트 클릭 및 입력, 스크롤 같은 기본 제어 구조가 구현되어 있음",
        "다만 현재 안정적으로 맞춰져 있는 환경은 폰과 폰 기반 테스트이며, 3개 하드웨어 트랙용 앱 구조는 안정화 이후 순차적으로 수정할 예정임",
    ], MARGIN_X, y) + 10

    team_box = (MARGIN_X, 940, PAGE_WIDTH - MARGIN_X, 1370)
    draw.rounded_rectangle(team_box, radius=22, fill=ACCENT, outline=BORDER, width=2)
    draw.text((team_box[0] + 24, team_box[1] + 20), "팀원별 현재 진행", font=H2_FONT, fill=PRIMARY)
    current_y = team_box[1] + 82
    current_y = draw_bullets(draw, [
        "이한솔: 하드웨어 확보, 전체 방향 수정, 투 트랙에서 3트랙으로의 구조 변경 정리, 앱 개발 흐름 조정, 발표 자료 및 진행 상황 정리 담당",
        "유세린: Raspberry Pi에 Android 설치 작업 진행 중",
        "정승원: Radxa에 Android 설치 작업 진행 중",
    ], team_box[0] + 8, current_y, (team_box[2] - team_box[0]) - 16)

    solo_box = (MARGIN_X, 1420, PAGE_WIDTH - MARGIN_X, 1640)
    draw.rounded_rectangle(solo_box, radius=22, fill="#F7FBFC", outline=BORDER, width=2)
    draw.text((solo_box[0] + 24, solo_box[1] + 20), "이한솔 중심 진행 내용", font=H2_FONT, fill=SECONDARY)
    draw_paragraph(
        draw,
        "이한솔은 하드웨어 구매 지연 상황에서 사비로 장비를 먼저 확보하여 개발이 중단되지 않도록 대응했고, "
        "PA03의 한계를 실제 테스트로 확인한 뒤 Radxa를 추가해 프로젝트 구조를 재정리하였다. 또한 현재 앱 개발 방향을 "
        "전체 기능 확장보다 토스 자동화 중심의 핵심 기능 검증으로 조정하여, 먼저 시연 가능한 형태를 만드는 데 집중하고 있다.",
        solo_box[0] + 24,
        solo_box[1] + 78,
        BODY_FONT,
        TEXT,
        (solo_box[2] - solo_box[0]) - 48,
        10,
    )

    draw_footer(draw, 3)
    return image


def build_page_four():
    image, draw = new_page()
    y = draw_header(draw, "3. 수정된 월별 계획")

    timeline = [
        (
            "2026년 3월",
            "주제 확정, 요구사항 분석, 시스템 구조 설계, 하드웨어 선정 및 발주를 계획했으나 "
            "발주 지연으로 인해 사비로 하드웨어를 우선 확보하는 방향으로 전환하였다."
        ),
        (
            "2026년 4월",
            "원래는 PA03 트랙 PoC를 중심으로 진행하려 했으나, 실제로는 PA03의 미러링 불안정 문제가 확인되어 "
            "앱 경량화가 필요한 상태가 되었다. 동시에 Raspberry Pi와 Radxa 트랙 PoC를 시작했고, "
            "Sender 앱과 Controller 앱 개발도 함께 착수하였다."
        ),
        (
            "2026년 5월",
            "Sender 앱과 Controller 앱을 본격적으로 개발하고, MCP 서버 구축과 LLM API 연동을 시작한다. "
            "이와 함께 단위 테스트와 토스 자동화 중심의 시나리오 테스트를 진행할 계획이다."
        ),
        (
            "2026년 6월",
            "트랙 간 성능 비교, 통합 테스트, 최종 데모 준비, 보고서 작성까지 포함하여 프로젝트를 마무리하는 방향으로 진행한다."
        ),
    ]

    current_y = y
    for index, (month, desc) in enumerate(timeline, start=1):
        box = (MARGIN_X, current_y, PAGE_WIDTH - MARGIN_X, current_y + 275)
        draw.rounded_rectangle(box, radius=22, fill=ACCENT, outline=BORDER, width=2)
        draw.text((box[0] + 24, box[1] + 20), f"{index}. {month}", font=H2_FONT, fill=PRIMARY)
        draw_paragraph(
            draw,
            desc,
            box[0] + 24,
            box[1] + 78,
            BODY_FONT,
            TEXT,
            (box[2] - box[0]) - 48,
            10,
        )
        current_y += 305

    note_box = (MARGIN_X, 1450, PAGE_WIDTH - MARGIN_X, 1640)
    draw.rounded_rectangle(note_box, radius=22, fill="#F7FBFC", outline=BORDER, width=2)
    draw.text((note_box[0] + 24, note_box[1] + 20), "운영 원칙", font=H2_FONT, fill=SECONDARY)
    draw_paragraph(
        draw,
        "기존에 작성했던 주별 계획은 실제 개발 상황과 차이가 커져 현재는 폐기하였다. "
        "이후 진행 관리는 월별 목표를 기준으로 정리하고, 각 미팅에서는 계획 대비 변경점과 이번 달 진행분을 중심으로 보고하는 방식으로 운영한다.",
        note_box[0] + 24,
        note_box[1] + 78,
        BODY_FONT,
        TEXT,
        (note_box[2] - note_box[0]) - 48,
        10,
    )

    draw_footer(draw, 4)
    return image


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    pages = [
        build_cover(),
        build_page_two(),
        build_page_three(),
        build_page_four(),
    ]
    pages[0].save(OUT_PREVIEW)
    pages[0].save(OUT_PDF, save_all=True, append_images=pages[1:], resolution=150.0)
    print(OUT_PDF)


if __name__ == "__main__":
    main()
