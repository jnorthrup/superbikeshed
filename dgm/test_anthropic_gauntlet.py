#!/usr/bin/env python3
"""
Test the LLM Agility Gauntlet with real Anthropic API calls.
"""

import os
import sys
from llm_agility_gauntlet import LLMAgility, AnthropicProvider

def test_with_real_anthropic():
    """Test with real Anthropic API if key is available"""
    api_key = os.getenv('ANTHROPIC_API_KEY')
    
    if not api_key:
        print("⚠️  No ANTHROPIC_API_KEY found in environment")
        print("💡 Set ANTHROPIC_API_KEY to test with real API")
        print("🔧 Using mock responses for demonstration...")
        api_key = "mock"
    else:
        print("🔑 Found API key, testing with real Anthropic Claude...")
    
    # Test different models
    models_to_test = [
        "claude-3-sonnet-20241022",
        "claude-3-haiku-20241022",
        "claude-3-5-sonnet-20241022"
    ]
    
    results = {}
    
    for model in models_to_test:
        print(f"\n🧪 Testing {model}")
        print("=" * 60)
        
        try:
            provider = AnthropicProvider(api_key=api_key, model=model)
            gauntlet = LLMAgility(provider)
            
            result = gauntlet.run_gauntlet()
            results[model] = result
            
            print(f"🎯 Score: {result.total_score:.1f}/100")
            print(f"⏱️  Time: {result.execution_time:.2f}s")
            print(f"✅ Correctness: {'PASS' if result.passed_correctness else 'FAIL'}")
            
            # Show segment breakdown
            for segment, score in result.segment_scores.items():
                print(f"   {segment}: {score:.1f}/33.3")
                
        except Exception as e:
            print(f"❌ Error testing {model}: {e}")
            results[model] = None
    
    # Summary comparison
    print(f"\n📊 SUMMARY COMPARISON")
    print("=" * 60)
    print(f"{'Model':<30} {'Score':<10} {'Time':<10} {'Pass':<6}")
    print("-" * 60)
    
    for model, result in results.items():
        if result:
            score = f"{result.total_score:.1f}"
            time_str = f"{result.execution_time:.2f}s"
            passed = "✅" if result.passed_correctness else "❌"
        else:
            score = "ERROR"
            time_str = "N/A"
            passed = "❌"
        
        print(f"{model:<30} {score:<10} {time_str:<10} {passed:<6}")
    
    return results

def analyze_transcript(result):
    """Analyze the conversation transcript for insights"""
    if not result or not result.transcript:
        return
    
    print(f"\n🔍 TRANSCRIPT ANALYSIS")
    print("=" * 40)
    
    for i, message in enumerate(result.transcript):
        role = "USER" if i % 2 == 0 else "ASSISTANT"
        print(f"\n[{role}]")
        if len(message) > 200:
            print(message[:200] + "...")
        else:
            print(message)

def main():
    """Main test runner"""
    print("🚀 LLM Agility Gauntlet - Anthropic Integration Test")
    print("=" * 60)
    
    # Run tests
    results = test_with_real_anthropic()
    
    # Show detailed analysis for best performer
    best_result = None
    best_score = 0
    
    for model, result in results.items():
        if result and result.total_score > best_score:
            best_score = result.total_score
            best_result = result
    
    if best_result:
        print(f"\n🏆 BEST PERFORMER ANALYSIS (Score: {best_score:.1f})")
        analyze_transcript(best_result)
    
    print(f"\n🎉 Test complete!")

if __name__ == "__main__":
    main()