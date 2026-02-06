import pytest
import time
import json
import os
import threading
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.service import Service as ChromeService
from selenium.webdriver.chrome.options import Options as ChromeOptions
from webdriver_manager.chrome import ChromeDriverManager
from app import create_app

@pytest.fixture(scope="module")
def app_server():
    app = create_app()
    app.config['TESTING'] = True
    
    def run_app():
        app.run(debug=False, host='127.0.0.1', port=5000, use_reloader=False)
    
    server_thread = threading.Thread(target=run_app, daemon=True)
    server_thread.start()
    time.sleep(2)  # Give server time to start
    yield "http://127.0.0.1:5000"

@pytest.fixture
def driver(app_server):
    chrome_options = ChromeOptions()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--disable-gpu")

    # Point directly to the Ubuntu system-installed driver
    # Usually located at /usr/bin/chromedriver or /usr/lib/chromium-browser/chromedriver
    service = ChromeService(executable_path='/usr/bin/chromedriver')
    
    try:
        driver = webdriver.Chrome(service=service, options=chrome_options)
    except Exception as e:
        pytest.skip(f"System Chrome/Chromium failed: {e}")

    yield driver
    driver.quit()


class TestWebInterface:
    def test_page_loads_successfully(self, driver, app_server):
        driver.get(app_server)
        assert "DevOps Testing Application" in driver.title
        h1_element = driver.find_element(By.TAG_NAME, "h1")
        assert "DevOps Testing Application" in h1_element.text
    
    def test_all_sections_present(self, driver, app_server):
        driver.get(app_server)
        
        sections = driver.find_elements(By.CLASS_NAME, "section")
        assert len(sections) >= 3
        
        section_texts = [section.text for section in sections]
        assert any("Users API" in text for text in section_texts)
        assert any("Products API" in text for text in section_texts)
        assert any("System Status" in text for text in section_texts)
    
    def test_users_api_buttons(self, driver, app_server):
        driver.get(app_server)
        
        get_users_btn = driver.find_element(By.XPATH, "//button[contains(text(), 'Get All Users')]")
        get_users_btn.click()
        
        results_div = WebDriverWait(driver, 10).until(
            EC.visibility_of_element_located((By.ID, "results"))
        )
        
        results_text = results_div.text
        assert "John Doe" in results_text
        assert "jane@example.com" in results_text
        
        try:
            parsed_data = json.loads(results_text)
            assert isinstance(parsed_data, list)
            assert len(parsed_data) > 0
        except json.JSONDecodeError:
            pytest.fail("Results are not valid JSON")
    
    def test_single_user_button(self, driver, app_server):
        driver.get(app_server)
        
        get_user_btn = driver.find_element(By.XPATH, "//button[contains(text(), 'Get User 1')]")
        get_user_btn.click()
        
        results_div = WebDriverWait(driver, 10).until(
            EC.visibility_of_element_located((By.ID, "results"))
        )
        
        results_text = results_div.text
        assert "John Doe" in results_text
        assert "john@example.com" in results_text
        
        try:
            parsed_data = json.loads(results_text)
            assert isinstance(parsed_data, dict)
            assert parsed_data['id'] == 1
            assert parsed_data['name'] == 'John Doe'
        except json.JSONDecodeError:
            pytest.fail("Results are not valid JSON")
    
    def test_products_api_buttons(self, driver, app_server):
        driver.get(app_server)
        
        get_products_btn = driver.find_element(By.XPATH, "//button[contains(text(), 'Get All Products')]")
        get_products_btn.click()
        
        results_div = WebDriverWait(driver, 10).until(
            EC.visibility_of_element_located((By.ID, "results"))
        )
        
        results_text = results_div.text
        assert "Laptop" in results_text
        assert "Mouse" in results_text
        
        try:
            parsed_data = json.loads(results_text)
            assert isinstance(parsed_data, list)
            assert len(parsed_data) > 0
        except json.JSONDecodeError:
            pytest.fail("Results are not valid JSON")
    
    def test_single_product_button(self, driver, app_server):
        driver.get(app_server)
        
        get_product_btn = driver.find_element(By.XPATH, "//button[contains(text(), 'Get Product 1')]")
        get_product_btn.click()
        
        results_div = WebDriverWait(driver, 10).until(
            EC.visibility_of_element_located((By.ID, "results"))
        )
        
        results_text = results_div.text
        assert "Laptop" in results_text
        assert "999.99" in results_text
        
        try:
            parsed_data = json.loads(results_text)
            assert isinstance(parsed_data, dict)
            assert parsed_data['id'] == 1
            assert parsed_data['name'] == 'Laptop'
            assert parsed_data['price'] == 999.99
        except json.JSONDecodeError:
            pytest.fail("Results are not valid JSON")
    
    def test_health_check_button(self, driver, app_server):
        driver.get(app_server)
        
        health_check_btn = driver.find_element(By.XPATH, "//button[contains(text(), 'Health Check')]")
        health_check_btn.click()
        
        results_div = WebDriverWait(driver, 10).until(
            EC.visibility_of_element_located((By.ID, "results"))
        )
        
        results_text = results_div.text
        assert "healthy" in results_text
        assert "devops-testing-app" in results_text
        
        try:
            parsed_data = json.loads(results_text)
            assert isinstance(parsed_data, dict)
            assert parsed_data['status'] == 'healthy'
            assert parsed_data['service'] == 'devops-testing-app'
        except json.JSONDecodeError:
            pytest.fail("Results are not valid JSON")
    
    def test_button_interactions(self, driver, app_server):
        driver.get(app_server)

        buttons = WebDriverWait(driver, 10).until(EC.presence_of_all_elements_located((By.TAG_NAME, "button")))
        assert len(buttons) >= 6
        
        for button in buttons:
            assert button.is_enabled()
            assert button.is_displayed()
        
        initial_results = driver.find_element(By.ID, "results").text
        assert initial_results == ""
        
        first_button = buttons[0]
        first_button.click()
        
        results_div = WebDriverWait(driver, 10).until(
            EC.visibility_of_element_located((By.ID, "results"))
        )
        
        updated_results = results_div.text
        assert updated_results != initial_results
        assert len(updated_results) > 0
    
    def test_responsive_design(self, driver, app_server):
        driver.get(app_server)
        
        driver.set_window_size(768, 1024)  # Tablet size
        time.sleep(1)
        h1_element = driver.find_element(By.TAG_NAME, "h1")
        assert h1_element.is_displayed()
        
        driver.set_window_size(375, 667)  # Mobile size
        time.sleep(1)
        h1_element = driver.find_element(By.TAG_NAME, "h1")
        assert h1_element.is_displayed()
        
        buttons = driver.find_elements(By.TAG_NAME, "button")
        for button in buttons:
            assert button.is_displayed()
        
        driver.set_window_size(1920, 1080)  # Desktop size
        time.sleep(1)
        h1_element = driver.find_element(By.TAG_NAME, "h1")
        assert h1_element.is_displayed()