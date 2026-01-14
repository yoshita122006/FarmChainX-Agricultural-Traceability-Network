import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class SimpleAIService {
  
  // ⚠️ PUT YOUR GROQ API KEY HERE
  private readonly GROQ_API_KEY = 'YOUR_GROQ_API_KEY_HERE';
  private readonly GROQ_API_URL = 'https://api.groq.com/openai/v1/chat/completions';
  
  constructor(private http: HttpClient) { }
  
 getFarmingGuide(cropName: string): Observable<any> {
    console.log('🌱 Getting AI info for:', cropName);
    
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${this.GROQ_API_KEY}`,
      'Content-Type': 'application/json'
    });
    
    // Improved prompt that works for ANY crop
    const prompt = this.createSmartPrompt(cropName);
    
    const body = {
      model: 'llama-3.1-8b-instant',
      messages: [{ role: 'user', content: prompt }],
      temperature: 0.7,
      max_tokens: 1500,
      response_format: { type: 'json_object' } // Force JSON response
    };
    
    return this.http.post(this.GROQ_API_URL, body, { headers }).pipe(
      map((response: any) => {
        console.log('✅ AI Raw Response:', response);
        return this.parseAIResponse(response, cropName);
      }),
      catchError(error => {
        console.error('❌ AI API Error:', error);
        // Return smart mock data based on crop
        return of(this.getSmartMockData(cropName));
      })
    );
  }
  
  private createSmartPrompt(cropName: string): string {
    return `You are AGRI-EXPERT, an agricultural specialist. Provide comprehensive farming information for "${cropName}" in this EXACT JSON format:

{
  "TYPE": "[e.g., Fruit/Vegetable/Grain/Legume/Nut/Herb/Spice/Commercial Crop]",
  "SCIENTIFIC_NAME": "[Scientific name]",
  "FAMILY": "[Plant family]",
  "CALORIES": "[Calories per 100g or 'Not applicable' for non-food crops]",
  "NUTRITION": "[Key nutrients if edible, otherwise growing requirements]",
  "SEASON": "[Growing season in India]",
  "PLANTING_TIME": "[Best planting time]",
  "HARVEST_TIME": "[Harvest time]",
  "CROP_DURATION": "[Growth duration in days/months]",
  "SOIL_PH": "[Ideal soil pH range]",
  "SOIL_TYPE": "[Preferred soil type]",
  "WATER_REQUIREMENT": "[Water needs - low/medium/high or mm/acre]",
  "TEMPERATURE": "[Optimal temperature range]",
  "RAINFALL": "[Rainfall requirement]",
  "SUNLIGHT": "[Sunlight needs - full sun/partial shade]",
  "COMMON_VARIETIES": "[Common varieties in India]",
  "MARKET_PRICE": "[Current market price range in ₹]",
  "STORAGE": "[Storage conditions]",
  "SHELF_LIFE": "[Shelf life]",
  "YIELD": "[Average yield per acre/hectare]",
  "COMMON_DISEASES": "[Common diseases]",
  "PEST_PROBLEMS": "[Common pests]",
  "HEALTH_BENEFITS": "[Health benefits if edible]",
  "USES": "[Primary uses - food/industry/medicinal/etc.]",
  "FUN_FACT": "[One interesting fact]"
}

IMPORTANT RULES:
1. Return ONLY valid JSON, no other text
2. If information is not available, use "Information not available"
3. For non-food crops like cotton, use appropriate fields
4. Make information practical for Indian farmers
5. Use metric units and Indian currency (₹)`;
  }
  
  private parseAIResponse(response: any, cropName: string): any {
    try {
      const content = response.choices[0]?.message?.content;
      console.log('📄 AI Response Content:', content);
      
      if (!content) {
        throw new Error('No content in AI response');
      }
      
      // Clean and parse JSON
      const cleanContent = content
        .replace(/```json|```/g, '') // Remove markdown
        .replace(/^JSON:\s*/i, '')   // Remove "JSON:" prefix
        .trim();
      
      console.log('🧹 Cleaned Content:', cleanContent);
      
      let data;
      try {
        data = JSON.parse(cleanContent);
      } catch (jsonError) {
        console.warn('JSON parse failed, trying text extraction');
        data = this.extractStructuredData(cleanContent);
      }
      
      // Validate and fill missing fields
      data = this.validateDataStructure(data, cropName);
      
      return {
        cropName: cropName,
        success: true,
        message: 'Real AI Data from Groq',
        data: data,
        timestamp: new Date().toISOString(),
        source: 'groq-ai'
      };
      
    } catch (error) {
      console.error('🔥 Parse Error:', error);
      return this.getSmartMockData(cropName);
    }
  }
  
  private extractStructuredData(text: string): any {
    const data: any = {};
    const lines = text.split('\n');
    
    for (const line of lines) {
      const match = line.match(/^([A-Z_]+):\s*(.+)$/i);
      if (match) {
        const key = match[1].trim().toUpperCase();
        const value = match[2].trim();
        if (key && value && !value.toLowerCase().includes('undefined')) {
          data[key] = value;
        }
      }
    }
    
    return data;
  }
  
  private validateDataStructure(data: any, cropName: string): any {
    const requiredFields = [
      'TYPE', 'SCIENTIFIC_NAME', 'FAMILY', 'CALORIES', 'NUTRITION',
      'SEASON', 'PLANTING_TIME', 'HARVEST_TIME', 'CROP_DURATION',
      'SOIL_PH', 'SOIL_TYPE', 'WATER_REQUIREMENT', 'TEMPERATURE',
      'RAINFALL', 'SUNLIGHT', 'COMMON_VARIETIES', 'MARKET_PRICE',
      'STORAGE', 'SHELF_LIFE', 'YIELD', 'COMMON_DISEASES',
      'PEST_PROBLEMS', 'HEALTH_BENEFITS', 'USES', 'FUN_FACT'
    ];
    
    const validatedData: any = {};
    
    for (const field of requiredFields) {
     if (data[field] && typeof data[field] === 'string' && data[field].trim() !== '') {
        validatedData[field] = data[field];
      } else {
        // Provide intelligent defaults based on crop type
        validatedData[field] = this.getDefaultValue(field, cropName);
      }
    }
    
    return validatedData;
  }
  
  private getDefaultValue(field: string, cropName: string): string {
    const lowerCrop = cropName.toLowerCase();
    
    const defaults: any = {
      'TYPE': 'Agricultural Crop',
      'SCIENTIFIC_NAME': 'Information not available',
      'FAMILY': 'Various',
      'CALORIES': 'Varies by variety',
      'NUTRITION': 'Rich in essential nutrients',
      'SEASON': 'Depends on region and climate',
      'PLANTING_TIME': 'Season dependent',
      'HARVEST_TIME': 'Season dependent',
      'CROP_DURATION': '90-180 days',
      'SOIL_PH': '6.0-7.0 (optimal)',
      'SOIL_TYPE': 'Well-drained fertile soil',
      'WATER_REQUIREMENT': 'Regular irrigation needed',
      'TEMPERATURE': 'Warm climate preferred',
      'RAINFALL': 'Adequate rainfall required',
      'SUNLIGHT': 'Full sun preferred',
      'COMMON_VARIETIES': 'Various local and hybrid varieties',
      'MARKET_PRICE': 'Varies by market and quality',
      'STORAGE': 'Cool, dry, well-ventilated area',
      'SHELF_LIFE': 'Depends on storage conditions',
      'YIELD': 'Varies by cultivation practices',
      'COMMON_DISEASES': 'Regular monitoring recommended',
      'PEST_PROBLEMS': 'Use integrated pest management',
      'HEALTH_BENEFITS': 'Promotes overall health',
      'USES': 'Food, commercial, or industrial uses',
      'FUN_FACT': 'Important agricultural commodity worldwide'
    };
    
    // Special cases based on crop type
    if (['wheat', 'rice', 'corn', 'barley'].some(g => lowerCrop.includes(g))) {
      if (field === 'TYPE') return 'Cereal Grain';
      if (field === 'WATER_REQUIREMENT') return 'Medium to high water needs';
    }
    
    if (['tomato', 'potato', 'onion', 'carrot'].some(v => lowerCrop.includes(v))) {
      if (field === 'TYPE') return 'Vegetable';
      if (field === 'SHELF_LIFE') return '1-4 weeks fresh';
    }
    
    if (['apple', 'banana', 'mango', 'orange'].some(f => lowerCrop.includes(f))) {
      if (field === 'TYPE') return 'Fruit';
      if (field === 'HEALTH_BENEFITS') return 'Rich in vitamins and antioxidants';
    }
    
    return defaults[field] || 'Information not available';
  }
  
  private getSmartMockData(cropName: string): any {
    console.log('📦 Using smart mock data for:', cropName);
    
    const lowerCrop = cropName.toLowerCase();
    let smartData: any = {};
    
    // Pre-defined data for common crops
    const cropDatabase: any = {
      'rice': {
        TYPE: 'Cereal Grain',
        SCIENTIFIC_NAME: 'Oryza sativa',
        FAMILY: 'Poaceae',
        CALORIES: '130 kcal per 100g',
        NUTRITION: 'Rich in carbohydrates, thiamine, niacin, iron',
        SEASON: 'Kharif (June-November)',
        PLANTING_TIME: 'June-July',
        HARVEST_TIME: 'September-November',
        CROP_DURATION: '90-150 days',
        SOIL_PH: '5.5-6.5',
        SOIL_TYPE: 'Clayey loam',
        WATER_REQUIREMENT: 'High (1000-2000mm)',
        TEMPERATURE: '20-35°C',
        RAINFALL: '1500-2000mm',
        SUNLIGHT: 'Full sun',
        COMMON_VARIETIES: 'Basmati, IR-64, Swarna',
        MARKET_PRICE: '₹25-60/kg',
        STORAGE: 'Airtight containers, 12-15% moisture',
        SHELF_LIFE: '6 months to 2 years',
        YIELD: '3-6 tons/hectare',
        COMMON_DISEASES: 'Blast, Bacterial blight, Sheath blight',
        PEST_PROBLEMS: 'Stem borer, Brown plant hopper',
        HEALTH_BENEFITS: 'Energy source, gluten-free',
        USES: 'Staple food, rice flour, beer',
        FUN_FACT: 'Rice feeds more than half of the world\'s population'
      },
      'tomato': {
        TYPE: 'Fruit Vegetable',
        SCIENTIFIC_NAME: 'Solanum lycopersicum',
        FAMILY: 'Solanaceae',
        CALORIES: '18 kcal per 100g',
        NUTRITION: 'Vitamin C, Vitamin K, potassium, lycopene',
        SEASON: 'Winter-Summer',
        PLANTING_TIME: 'February-March',
        HARVEST_TIME: 'May-July',
        CROP_DURATION: '70-90 days',
        SOIL_PH: '6.0-6.8',
        SOIL_TYPE: 'Well-drained loam',
        WATER_REQUIREMENT: '1-1.5 inches weekly',
        TEMPERATURE: '21-24°C',
        RAINFALL: '600-900mm',
        SUNLIGHT: 'Full sun (6-8 hours)',
        COMMON_VARIETIES: 'Hybrid 626, Roma, Cherry',
        MARKET_PRICE: '₹20-40/kg',
        STORAGE: 'Room temp until ripe, then refrigerate',
        SHELF_LIFE: '1-2 weeks fresh',
        YIELD: '25-40 tons/hectare',
        COMMON_DISEASES: 'Blight, Blossom end rot, Fusarium wilt',
        PEST_PROBLEMS: 'Whitefly, Aphids, Tomato hornworm',
        HEALTH_BENEFITS: 'Antioxidants, heart health, cancer prevention',
        USES: 'Fresh, sauces, ketchup, salads',
        FUN_FACT: 'Tomatoes were once believed to be poisonous in Europe'
      },
      'potato': {
        TYPE: 'Tuber Vegetable',
        SCIENTIFIC_NAME: 'Solanum tuberosum',
        FAMILY: 'Solanaceae',
        CALORIES: '77 kcal per 100g',
        NUTRITION: 'Vitamin C, potassium, Vitamin B6, fiber',
        SEASON: 'Rabi (Winter)',
        PLANTING_TIME: 'October-November',
        HARVEST_TIME: 'January-February',
        CROP_DURATION: '90-120 days',
        SOIL_PH: '5.0-6.5',
        SOIL_TYPE: 'Sandy loam',
        WATER_REQUIREMENT: '500-700mm',
        TEMPERATURE: '15-20°C',
        RAINFALL: '300-500mm',
        SUNLIGHT: 'Full sun',
        COMMON_VARIETIES: 'Kufri Jyoti, Kufri Bahar',
        MARKET_PRICE: '₹15-25/kg',
        STORAGE: '4-7°C, dark, ventilated',
        SHELF_LIFE: '4-6 months cold storage',
        YIELD: '20-35 tons/hectare',
        COMMON_DISEASES: 'Late blight, Early blight, Black scurf',
        PEST_PROBLEMS: 'Colorado potato beetle, Aphids',
        HEALTH_BENEFITS: 'Antioxidants, digestive health',
        USES: 'Chips, fries, mashed, boiled',
        FUN_FACT: 'Potatoes were the first vegetable grown in space'
      },
      // Add more crops as needed
    };
    
    // Find matching crop
    for (const [key, value] of Object.entries(cropDatabase)) {
      if (lowerCrop.includes(key)) {
        smartData = value;
        break;
      }
    }
    
    // If no match, use generic data with crop-specific adjustments
    if (Object.keys(smartData).length === 0) {
      smartData = this.validateDataStructure({}, cropName);
    }
    
    return {
      cropName: cropName,
      success: false,
      message: 'Using enhanced agricultural database',
      data: smartData,
      timestamp: new Date().toISOString(),
      source: 'local-database'
    };
  }
}